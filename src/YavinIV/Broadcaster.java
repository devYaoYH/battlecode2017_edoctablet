package YavinIV;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

import java.util.ArrayList;
import java.util.ArrayDeque;

public class Broadcaster {

    //Debugging toggle
    private boolean debug = false;

    //Robot Controller
    static RobotController rc;

    //Grid array starting indices
    //We store a grid of 60x60 (10 x 10 area squares) positions for map awareness
    static final int OFFSET = 1000;
    static final int HB_OFFSET = 100000;
    static final int DATA_RELEVANCE = 50;
    static final int SITREP_RELEVANCE = 3;
    static final float GRID_SIZE = 10f;

    static final float TEST_CIRCLE_SIZE = 0.5f;

    //Boundaries of map
    static int x_upper = 60;
    static int x_lower = 0;
    static int y_upper = 60;
    static int y_lower = 0;
    static final int X_UPPERBOUND = 5;
    static final int X_LOWERBOUND = 6;
    static final int Y_UPPERBOUND = 7;
    static final int Y_LOWERBOUND = 8;

    //BFS to find closest un-updated grid for recee
    static final int MAX_STACK_DEPTH = 50;
    static int bfs_depth = 0;
    static int[][] adjacent_grids = {{1,0},{0,-1},{-1,0},{0,1}};        //RIGHT|DOWN|LEFT|UP
    private CoordsQueue neighbours = new CoordsQueue();
    //private ArrayDeque<int[]> neighbours = new ArrayDeque<>();
    private ArrayList<Integer> visitedArr = new ArrayList<Integer>();

    public Broadcaster(RobotController robot_control) throws GameActionException {
        rc = robot_control;
        //Reads updated boundaries if available
        update();
    }

    public void update() throws GameActionException {
        //Reads updated boundaries if available
        int tmp_x_upper = rc.readBroadcast(X_UPPERBOUND);
        int tmp_x_lower = rc.readBroadcast(X_LOWERBOUND);
        int tmp_y_upper = rc.readBroadcast(Y_UPPERBOUND);
        int tmp_y_lower = rc.readBroadcast(Y_LOWERBOUND);
        if (tmp_x_upper != 0) x_upper = tmp_x_upper;
        if (tmp_x_lower != 0) x_lower = tmp_x_lower;
        if (tmp_y_upper != 0) y_upper = tmp_y_upper;
        if (tmp_y_lower != 0) y_lower = tmp_y_lower;
    }

    public String printBoundaries(){
        String output = "";
        output += "x_upper: "+Integer.toString(x_upper);
        output += "| x_lower: "+Integer.toString(x_lower);
        output += "| y_upper: "+Integer.toString(y_upper);
        output += "| y_lower: "+Integer.toString(y_lower);
        return output;
    }

    public void updateBoundaries(MapLocation curLoc) throws GameActionException {
        int[] cur_grid = channel_to_grid(location_to_channel(curLoc));
        int new_x_upper = -1;
        int new_x_lower = -1;
        int new_y_upper = -1;
        int new_y_lower = -1;

        //Test the 4 directions for map edges
        //RIGHT
        Direction testDir = new Direction(0f);
        if (!rc.onTheMap(curLoc.add(testDir,GRID_SIZE/2),TEST_CIRCLE_SIZE)){
            //cur_grid[0] = (int)Math.floor(curLoc.x/10);
            new_x_upper = cur_grid[0];
        }
        //DOWN
        testDir = testDir.rotateRightRads((float)Math.PI/2);
        if (!rc.onTheMap(curLoc.add(testDir,GRID_SIZE/2),TEST_CIRCLE_SIZE)){
            //cur_grid[1] = (int)Math.ceil(curLoc.y/10);
            new_y_lower = cur_grid[1];
        }
        //LEFT
        testDir = testDir.rotateRightRads((float)Math.PI/2);
        if (!rc.onTheMap(curLoc.add(testDir,GRID_SIZE/2),TEST_CIRCLE_SIZE)){
            //cur_grid[0] = (int)Math.ceil(curLoc.x/10);
            new_x_lower = cur_grid[0];
        }
        //UP
        testDir = testDir.rotateRightRads((float)Math.PI/2);
        if (!rc.onTheMap(curLoc.add(testDir,GRID_SIZE/2),TEST_CIRCLE_SIZE)){
            //cur_grid[1] = (int)Math.floor(curLoc.y/10);
            new_y_upper = cur_grid[1];
        }

        //Update values
        if (new_x_upper != -1){
            rc.broadcast(X_UPPERBOUND, new_x_upper);
            x_upper = new_x_upper;
        }
        if (new_x_lower != -1){
            rc.broadcast(X_LOWERBOUND, new_x_lower);
            x_lower = new_x_lower;
        }
        if (new_y_upper != -1){
            rc.broadcast(Y_UPPERBOUND, new_y_upper);
            y_upper = new_y_upper;
        }
        if (new_y_lower != -1){
            rc.broadcast(Y_LOWERBOUND, new_y_lower);
            y_lower = new_y_lower;
        }

        //See if current location will expand present boundaries
        if (cur_grid[0] > x_upper) x_upper = cur_grid[0];
        if (cur_grid[0] < x_lower) x_lower = cur_grid[0];
        if (cur_grid[1] > y_upper) y_upper = cur_grid[1];
        if (cur_grid[1] < y_lower) y_lower = cur_grid[1];
    }

    public void updateGrid(MapLocation loc, int hb, int msg) throws GameActionException {
        int[] locGrid = channel_to_grid(location_to_channel(loc));
        int x = locGrid[0];
        int y = locGrid[1];
        if (x > 99 || y > 99) return;
        rc.broadcast(x*100+y+OFFSET,format_message(hb,msg));
    }

    public void updateGrid(int x, int y, int hb, int msg) throws GameActionException {
        if (x > 99 || y > 99) return;
        rc.broadcast(x*100+y+OFFSET,format_message(hb,msg));
    }

    public int[] readGrid(MapLocation loc) throws GameActionException {
        int[] locGrid = channel_to_grid(location_to_channel(loc));
        int x = locGrid[0];
        int y = locGrid[1];
        if (x > 99 || y > 99) return null;
        //Checks if grid is up to date and returns latest grid message
        int grid_data = rc.readBroadcast(x*100+y+OFFSET);
        int[] data = decompose_data(grid_data);
        //Check if message is just a sitRep
        if (data[1] == 0){
            //Sit rep
            if (data[0] <= Math.max(0, rc.getRoundNum() - SITREP_RELEVANCE)){
                //Obsolete
                data[0] = 0;
            }
            else data[0] = 1;
        }
        else {
            if (data[0] <= Math.max(0, rc.getRoundNum() - DATA_RELEVANCE)) {
                //Obsolete
                data[0] = 0;
            }
            else data[0] = 1;
        }
        return data;
    }

    public int[] readGrid(int x, int y) throws GameActionException {
        if (x > 99 || y > 99) return null;
        //Checks if grid is up to date and returns latest grid message
        int grid_data = rc.readBroadcast(x*100+y+OFFSET);
        int[] data = decompose_data(grid_data);
        if (data[0] < Math.max(0, rc.getRoundNum() - DATA_RELEVANCE)){
            //Obsolete
            data[0] = 0;
        }
        else data[0] = 1;
        return data;
    }

    public boolean isValidGrid(MapLocation targetGrid){
        int[] target_grid = channel_to_grid(location_to_channel(targetGrid));
        System.out.println("Testing coords: x("+Integer.toString(target_grid[0])+") y("+Integer.toString(target_grid[1])+")");
        return !(target_grid[0] > x_upper || target_grid[0] < x_lower || target_grid[1] > y_upper || target_grid[1] < y_lower);
    }

    private boolean isValidGrid(int[] target_grid){
        System.out.println("Testing in BFS function...");
        System.out.println("Testing coords: x("+Integer.toString(target_grid[0])+") y("+Integer.toString(target_grid[1])+")");
        return !(target_grid[0] > x_upper || target_grid[0] < x_lower || target_grid[1] > y_upper || target_grid[1] < y_lower);
    }

    //BFS interface, resets mem and gets ready to run bfs on graph
    public MapLocation nextEmptyGrid(MapLocation curLoc) throws GameActionException {
        int[] cur_grid = channel_to_grid(location_to_channel(curLoc));
        final int cur_x = cur_grid[0];
        final int cur_y = cur_grid[1];
        int[] new_grid = new int[2];
        bfs_depth = 0;
        neighbours.clear();
        visitedArr.clear();
        System.out.println("neighbours Size: "+Integer.toString(neighbours.size()));
        visitedArr.add(location_to_channel(curLoc));
        for (int i=0;i<adjacent_grids.length;i++){
            new_grid[0] = cur_x + adjacent_grids[i][0];
            new_grid[1] = cur_y + adjacent_grids[i][1];
            if (isValidGrid(new_grid)){
                neighbours.offer(new_grid);
                System.out.println("peeking: "+Integer.toString(grid_to_channel(neighbours.peek())));
            }
            else continue;
            System.out.println("Pushed new coords");
            System.out.println("New coords: x("+Integer.toString(new_grid[0])+") y("+Integer.toString(new_grid[1])+")");
        }
        System.out.println("FINISHED INITIALIZATION");
        int[] next_closest_empty_grid = bfs_empty_grid();
        if (next_closest_empty_grid != null) return channel_to_location(grid_to_channel(next_closest_empty_grid));
        else return curLoc;
    }

    //Actual bfs function
    private int[] bfs_empty_grid() throws GameActionException {
        System.out.println("neighbours Size: "+Integer.toString(neighbours.size()));
        System.out.println("peeking: "+Integer.toString(grid_to_channel(neighbours.peek())));
        while(!neighbours.isEmpty()){
            if (bfs_depth > MAX_STACK_DEPTH) return null;
            int[] cur_grid = neighbours.poll();
            if (visitedArr.contains(grid_to_channel(cur_grid))) continue;
            visitedArr.add(grid_to_channel(cur_grid));
            System.out.println("Popping fresh coords");
            System.out.println("Pop coords: x("+Integer.toString(cur_grid[0])+") y("+Integer.toString(cur_grid[1])+")");
            bfs_depth++;
            if (debug) rc.setIndicatorDot(channel_to_location(grid_to_channel(cur_grid)), 0, 0, 0);
            int[] cur_data = readGrid(cur_grid[0],cur_grid[1]);
            if (cur_data[0] == 0) return cur_grid;
            else{
                int[] new_grid = {cur_grid[0],cur_grid[1]};
                for (int i=0;i<adjacent_grids.length;i++){
                    new_grid[0] = cur_grid[0] + adjacent_grids[i][0];
                    new_grid[1] = cur_grid[1] + adjacent_grids[i][1];
                    if (isValidGrid(new_grid)){
                        neighbours.offer(new_grid);
                        System.out.println("Pushed new coords");
                        System.out.println("New coords: x("+Integer.toString(new_grid[0])+") y("+Integer.toString(new_grid[1])+")");
                    }
                }
            }
        }
        return null;
    }

    //Return nearest valid grid point
    public MapLocation nearestValidGrid(MapLocation test_grid){
        int[] cur_grid = channel_to_grid(location_to_channel(test_grid));
        int[] new_grid = new int[2];
        for (int i=0;i<adjacent_grids.length;i++) {
            new_grid[0] = cur_grid[0] + adjacent_grids[i][0];
            new_grid[1] = cur_grid[1] + adjacent_grids[i][1];
            MapLocation newLoc = channel_to_location(grid_to_channel(new_grid));
            if (isValidGrid(newLoc)) return newLoc;
        }
        return null;
    }

    private int format_message(int hb, int msg){
        return hb*HB_OFFSET+msg;
    }

    private int[] decompose_data(int data){
        int[] decomp_data = new int[2];
        int hb = data/HB_OFFSET;
        int msg = data-hb*HB_OFFSET;
        decomp_data[0] = hb;
        decomp_data[1] = msg;
        return decomp_data;
    }

    static int grid_to_channel(int[] gridCoords){
        return gridCoords[0]*100+gridCoords[1]+OFFSET;
    }

    static int[] channel_to_grid(int channel){
        int[] new_grid = new int[2];
        new_grid[0] = (channel-OFFSET)/100;
        new_grid[1] = channel - (channel/100)*100;
        return new_grid;
    }

    static MapLocation channel_to_location(int channel){
        int x_coord = (channel-OFFSET)/100;
        int y_coord = channel-OFFSET-x_coord*100;
        return new MapLocation(x_coord*GRID_SIZE,y_coord*GRID_SIZE);
    }

    static int location_to_channel(MapLocation loc){
        int x_coord = Math.round(loc.x/10);
        int y_coord = Math.round(loc.y/10);
        return x_coord*100+y_coord+OFFSET;
    }

}
