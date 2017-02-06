package tatooine;

import battlecode.common.*;

import java.util.Iterator;
import java.util.Queue;
import java.util.AbstractQueue;

public class Broadcaster {

    //Robot Controller
    static RobotController rc;

    //Grid array starting indices
    //We store a grid of 60x60 (10 x 10 area squares) positions for map awareness
    static final int OFFSET = 1000;
    static final int HB_OFFSET = 100000;
    static final int DATA_RELEVANCE = 10;
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
    static final int MAX_STACK_DEPTH = 100;
    static int bfs_depth = 0;
    static int[][] adjacent_grids = {{1,0},{0,-1},{-1,0},{0,1}};        //RIGHT|DOWN|LEFT|UP
    static AbstractQueue<int[]> neighbours = new AbstractQueue<int[]>() {
        private Queue<int[]> q;
        @Override
        public int size() {
            return q.size();
        }

        @Override
        public Iterator<int[]> iterator() {
            return null;
        }

        @Override
        public boolean offer(int[] item){
            return q.add(item);
        }

        @Override
        public int[] poll(){
            return q.poll();
        }

        @Override
        public int[] peek() {
            return q.peek();
        }
    };

    public void Broadcaster(RobotController robot_control) throws GameActionException {
        rc = robot_control;
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
            new_x_upper = cur_grid[0];
        }
        //DOWN
        testDir = testDir.rotateRightRads((float)Math.PI/2);
        if (!rc.onTheMap(curLoc.add(testDir,GRID_SIZE/2),TEST_CIRCLE_SIZE)){
            new_y_lower = cur_grid[1];
        }
        //LEFT
        testDir = testDir.rotateRightRads((float)Math.PI/2);
        if (!rc.onTheMap(curLoc.add(testDir,GRID_SIZE/2),TEST_CIRCLE_SIZE)){
            new_x_lower = cur_grid[0];
        }
        //UP
        testDir = testDir.rotateRightRads((float)Math.PI/2);
        if (!rc.onTheMap(curLoc.add(testDir,GRID_SIZE/2),TEST_CIRCLE_SIZE)){
            new_y_upper = cur_grid[1];
        }
        if (new_x_upper != -1) rc.broadcast(X_UPPERBOUND, new_x_upper);
        if (new_x_lower != -1) rc.broadcast(X_LOWERBOUND, new_x_lower);
        if (new_y_upper != -1) rc.broadcast(Y_UPPERBOUND, new_y_upper);
        if (new_y_lower != -1) rc.broadcast(Y_LOWERBOUND, new_y_lower);
    }

    public void updateGrid(int x, int y, int ID, int hb, int msg) throws GameActionException {
        if (x > 99 || y > 99) return;
        rc.broadcast(x*100+y+OFFSET,format_message(hb,msg));
    }

    public int[] readGrid(int x, int y) throws GameActionException {
        if (x > 99 || y > 99) return null;
        //Checks if grid is up to date and returns latest grid message
        int grid_data = rc.readBroadcast(x*100+y+OFFSET);
        int[] data = decompose_data(grid_data);
        if (data[0] < rc.getRoundNum() - DATA_RELEVANCE){
            data[0] = 0;
        }
        else data[0] = 1;
        return data;
    }

    //BFS interface, resets mem and gets ready to run bfs on graph
    public int nextEmptyGrid(int x, int y) throws GameActionException {
        int[] cur_grid = {x,y};
        int[] new_grid = {x,y};
        bfs_depth = 0;
        neighbours.clear();
        for (int i=0;i<adjacent_grids.length;i++){
            new_grid[0] = cur_grid[0] + adjacent_grids[i][0];
            new_grid[1] = cur_grid[1] + adjacent_grids[i][1];
            if (new_grid[0] > x_upper || new_grid[0] < x_lower || new_grid[1] > y_upper || new_grid[1] < y_lower) continue;
            neighbours.offer(new_grid);
        }
        return grid_to_channel(bfs_empty_grid());
    }

    //Actual bfs function
    private int[] bfs_empty_grid() throws GameActionException {
        while(!neighbours.isEmpty()){
            if (bfs_depth > MAX_STACK_DEPTH) return null;
            int[] cur_grid = neighbours.poll();
            bfs_depth++;
            int[] cur_data = readGrid(cur_grid[0],cur_grid[1]);
            if (cur_data[0] == 0) return cur_grid;
            else{
                int[] new_grid = {cur_grid[0],cur_grid[1]};
                for (int i=0;i<adjacent_grids.length;i++){
                    new_grid[0] = cur_grid[0] + adjacent_grids[i][0];
                    new_grid[1] = cur_grid[1] + adjacent_grids[i][1];
                    if (new_grid[0] > x_upper || new_grid[0] < x_lower || new_grid[1] > y_upper || new_grid[1] < y_lower) continue;
                    neighbours.offer(new_grid);
                }
            }
        }
        return null;
    }

    private int format_message(int hb, int msg){
        return hb*HB_OFFSET+msg;
    }

    private int[] decompose_data(int data){
        int[] decomp_data = new int[2];
        int hb = data/HB_OFFSET;
        int msg = data-hb;
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
