package Kamino;

import battlecode.common.*;

import java.util.ArrayList;

public class Broadcaster {

    //Debugging toggle
    private boolean debug = false;

    //Robot Controller
    static RobotController rc;

    //Grid array starting indices
    //We store a grid of 60x60 (10 x 10 area squares) positions for map awareness
    private int OFFSET = 2000;
    static final int HB_OFFSET = 100000;
    static final int DATA_RELEVANCE = 150;
    static final int CONTACTREP_RELEVANCE = 15;
    static final int SITREP_RELEVANCE = 25;
    static final float GRID_SIZE = 10f;

    static final float TEST_CIRCLE_SIZE = 0.5f;

    //Serialized Arrays
    //5 x 400 channels (2.5k reserved - 100 overflow channels)
    static final int MAX_SIZE = 20;
    private int ARR_LEN = 21;
    private int MAX_X = 21;
    private int MAX_Y = 21;
    private int OFFSET_X = -999;
    private int OFFSET_Y = -999;
    static final int BROADCASTER_INITIALIZED = 1995;
    static final int BROADCASTER_INITIALIZE_X = 1996;
    static final int BROADCASTER_INITIALIZE_Y = 1997;

    //Boundaries of map
    private int x_upper = 60;
    private int x_lower = 0;
    private int y_upper = 60;
    private int y_lower = 0;
    static final int X_UPPERBOUND = 5;
    static final int X_LOWERBOUND = 6;
    static final int Y_UPPERBOUND = 7;
    static final int Y_LOWERBOUND = 8;

    //BFS to find closest un-updated grid for recee
    static final int MAX_STACK_DEPTH = 50;
    private int bfs_depth = 0;
    static final int[][] adjacent_grids = {{1,0},{0,-1},{-1,0},{0,1}};        //RIGHT|DOWN|LEFT|UP
    private CoordsQueue neighbours = new CoordsQueue();
    private ArrayList<Integer> visitedArr = new ArrayList<Integer>();

    public Broadcaster(RobotController robot_control, int CHANNEL) throws GameActionException {
        rc = robot_control;
        OFFSET = CHANNEL;
        //Reads updated boundaries if available
        initialize();
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

    private void initialize() throws GameActionException {
        if (OFFSET_X == -999 || OFFSET_Y == -999){
            if (rc.readBroadcast(BROADCASTER_INITIALIZED) == 1){
                OFFSET_X = rc.readBroadcast(BROADCASTER_INITIALIZE_X);
                OFFSET_Y = rc.readBroadcast(BROADCASTER_INITIALIZE_Y);
            }
            else{
                setInitialize();
            }
        }
    }

    private void setInitialize() throws GameActionException {
        rc.broadcast(BROADCASTER_INITIALIZED, 1);
        OFFSET_X = Math.round(rc.getLocation().x/10)-MAX_SIZE/2;
        OFFSET_Y = Math.round(rc.getLocation().y/10)-MAX_SIZE/2;
        MAX_X = MAX_SIZE + OFFSET_X;
        MAX_Y = MAX_SIZE + OFFSET_Y;
        if (OFFSET_X < 0){
            OFFSET_X = 0;
        }
        if (OFFSET_Y < 0){
            OFFSET_Y = 0;
        }
        ARR_LEN = MAX_X - OFFSET_X + 1;
        rc.broadcast(BROADCASTER_INITIALIZE_X, OFFSET_X);
        rc.broadcast(BROADCASTER_INITIALIZE_Y, OFFSET_Y);
        rc.broadcast(X_LOWERBOUND, OFFSET_X);
        rc.broadcast(Y_LOWERBOUND, OFFSET_Y);
        rc.broadcast(X_UPPERBOUND, MAX_X);
        rc.broadcast(Y_UPPERBOUND, MAX_Y);
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
        if (x > 99 || y > 99 || x < 0 || y < 0) return;
        rc.broadcast(grid_to_channel(locGrid),format_message(hb,msg));
    }

    public void updateGrid(int x, int y, int hb, int msg) throws GameActionException {
        if (x > 99 || y > 99 || x < 0 || y < 0) return;
        int[] grid = new int[2];
        grid[0] = x;
        grid[1] = y;
        rc.broadcast(grid_to_channel(grid),format_message(hb,msg));
    }

    public int[] readGrid(MapLocation loc) throws GameActionException {
        int[] locGrid = channel_to_grid(location_to_channel(loc));
        int x = locGrid[0];
        int y = locGrid[1];
        if (x > 99 || y > 99 || x < 0 || y < 0){
            int[] nullGrid = {0,0};
            return nullGrid;
        }
        //Checks if grid is up to date and returns latest grid message
        int grid_data = rc.readBroadcast(location_to_channel(loc));
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
            else{
                data[0] = 1;
                Message tmpMsg = new Message(data[1]);
                if (tmpMsg.getMessageType() == Message.CONTACT_REP){
                    if (data[0] <= Math.max(0, rc.getRoundNum() - CONTACTREP_RELEVANCE)) {
                        //Obsolete
                        data[0] = 0;
                    }
                }
            }
        }
        return data;
    }

    public int[] readGrid(int x, int y) throws GameActionException {
        if (x > 99 || y > 99 || x < 0 || y < 0){
            int[] nullGrid = {0,0};
            return nullGrid;
        }
        //Checks if grid is up to date and returns latest grid message
        int[] readGrid = new int[2];
        readGrid[0] = x;
        readGrid[1] = y;
        int grid_data = rc.readBroadcast(grid_to_channel(readGrid));
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
        return !(target_grid[0] > x_upper || target_grid[0] < x_lower || target_grid[1] > y_upper || target_grid[1] < y_lower);
    }

    private boolean isValidGrid(int[] target_grid){
        return !(target_grid[0] > x_upper || target_grid[0] < x_lower || target_grid[1] > y_upper || target_grid[1] < y_lower);
    }

    //<-------------------------- BFS TO EMPTY GRID -------------------------->
    //BFS interface, resets mem and gets ready to run bfs on graph
    public MapLocation nextEmptyGrid(MapLocation curLoc) throws GameActionException {
        int[] cur_grid = channel_to_grid(location_to_channel(curLoc));
        int cur_x = cur_grid[0];
        int cur_y = cur_grid[1];
        int[] new_grid = new int[2];
        bfs_depth = 0;
        neighbours.clear();
        visitedArr.clear();
        visitedArr.add(location_to_channel(curLoc));
        for (int i=0;i<adjacent_grids.length;i++){
            new_grid[0] = cur_x + adjacent_grids[i][0];
            new_grid[1] = cur_y + adjacent_grids[i][1];
            if (isValidGrid(new_grid)){
                neighbours.offer(new_grid);
            }
        }
        int[] next_closest_empty_grid = bfs_empty_grid();
        if (next_closest_empty_grid != null) return channel_to_location(grid_to_channel(next_closest_empty_grid));
        else return curLoc;
    }

    //Actual bfs function
    private int[] bfs_empty_grid() throws GameActionException {
        while(!neighbours.isEmpty()){
            if (bfs_depth > MAX_STACK_DEPTH) return null;
            //todo when not enough bytecodes, prevent robot from getting stuck...
            if (Clock.getBytecodeNum() > BasePlayer.BYTE_THRESHOLD-2500) return null;
            int[] cur_grid = neighbours.poll();
            int cur_x = cur_grid[0];
            int cur_y = cur_grid[1];
            if (visitedArr.contains(grid_to_channel(cur_grid))) continue;
            visitedArr.add(grid_to_channel(cur_grid));
            bfs_depth++;
            if (debug) rc.setIndicatorDot(channel_to_location(grid_to_channel(cur_grid)), 0, 0, 0);
            int[] cur_data = readGrid(cur_grid[0],cur_grid[1]);
            //todo bfs to empty grid (un-scouted)
            if (cur_data[0] == 0){
                if (debug) rc.setIndicatorDot(channel_to_location(grid_to_channel(cur_grid)), 0, 255, 0);
                return cur_grid;
            }
            else{
                int[] new_grid = {cur_x,cur_y};
                for (int i=0;i<adjacent_grids.length;i++){
                    new_grid[0] = cur_grid[0] + adjacent_grids[i][0];
                    new_grid[1] = cur_grid[1] + adjacent_grids[i][1];
                    if (isValidGrid(new_grid)){
                        neighbours.offer(new_grid);
                    }
                }
            }
        }
        return null;
    }

    //<------------------------ BFS TO OCCUPIED GRID ------------------------->
    //BFS interface, resets mem and gets ready to run bfs on graph
    public MapLocation nextOccupiedGrid(MapLocation curLoc) throws GameActionException {
        int[] cur_grid = channel_to_grid(location_to_channel(curLoc));
        final int cur_x = cur_grid[0];
        final int cur_y = cur_grid[1];
        int[] new_grid = new int[2];
        bfs_depth = 0;
        neighbours.clear();
        visitedArr.clear();
        visitedArr.add(location_to_channel(curLoc));
        for (int i=0;i<adjacent_grids.length;i++){
            new_grid[0] = cur_x + adjacent_grids[i][0];
            new_grid[1] = cur_y + adjacent_grids[i][1];
            if (isValidGrid(new_grid)){
                neighbours.offer(new_grid);
            }
        }
        int[] next_closest_empty_grid = bfs_occupied_grid();
        if (next_closest_empty_grid != null) return channel_to_location(grid_to_channel(next_closest_empty_grid));
        else return curLoc;
    }

    //Actual bfs function
    private int[] bfs_occupied_grid() throws GameActionException {
        while(!neighbours.isEmpty()){
            if (bfs_depth > MAX_STACK_DEPTH) return null;
            //todo when not enough bytecodes, prevent robot from getting stuck...
            //if (Clock.getBytecodeNum() > BasePlayer.BYTE_THRESHOLD) return null;
            int[] cur_grid = neighbours.poll();
            int cur_x = cur_grid[0];
            int cur_y = cur_grid[1];
            if (visitedArr.contains(grid_to_channel(cur_grid))) continue;
            visitedArr.add(grid_to_channel(cur_grid));
            bfs_depth++;
            if (debug) rc.setIndicatorDot(channel_to_location(grid_to_channel(cur_grid)), 0, 0, 0);
            int[] cur_data = readGrid(cur_grid[0],cur_grid[1]);
            //todo bfs to occupied grid (msg active)
            if (cur_data[0] == 1 && cur_data[1] != 0) return cur_grid;
            else{
                int[] new_grid = {cur_x,cur_y};
                for (int i=0;i<adjacent_grids.length;i++){
                    new_grid[0] = cur_grid[0] + adjacent_grids[i][0];
                    new_grid[1] = cur_grid[1] + adjacent_grids[i][1];
                    if (isValidGrid(new_grid)){
                        neighbours.offer(new_grid);
                    }
                }
            }
        }
        return null;
    }

    //Return nearest valid grid point
    public MapLocation nearestValidGrid(MapLocation test_grid){
        MapLocation testGrid = test_grid;
        int[] cur_grid = channel_to_grid(location_to_channel(testGrid));
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

    public int grid_to_channel(int[] gridCoords){
        int[] curCoords = gridCoords;
        int[] cur_grid = {curCoords[0], curCoords[1]};
        cur_grid[0] -= OFFSET_X;
        cur_grid[1] -= OFFSET_Y;
        return cur_grid[0]+cur_grid[1]*(ARR_LEN)+OFFSET;
    }

    public int[] channel_to_grid(int channel){
        int cur_channel = channel;
        cur_channel -= OFFSET;
        int[] new_grid = new int[2];
        new_grid[1] = (int)Math.floor(cur_channel/(ARR_LEN));
        new_grid[0] = cur_channel-new_grid[1]*(ARR_LEN);
        new_grid[0] += OFFSET_X;
        new_grid[1] += OFFSET_Y;
        return new_grid;
    }

    public MapLocation channel_to_location(int channel){
        int cur_channel = channel;
        cur_channel -= OFFSET;
        int y_coord = (int)Math.floor(cur_channel/(ARR_LEN));
        int x_coord = cur_channel-y_coord*(ARR_LEN);
        x_coord += OFFSET_X;
        y_coord += OFFSET_Y;
        return new MapLocation(x_coord*GRID_SIZE,y_coord*GRID_SIZE);
    }

    public int location_to_channel(MapLocation loc){
        MapLocation curLoc = loc;
        int x_coord = Math.round(curLoc.x/GRID_SIZE);
        int y_coord = Math.round(curLoc.y/GRID_SIZE);
        x_coord -= OFFSET_X;
        y_coord -= OFFSET_Y;
        return x_coord+y_coord*(ARR_LEN)+OFFSET;
    }

}
