package Alderaan;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public strictfp class RobotPlayer {
    static RobotController rc;

	//Precomp Degrees
	static float DEG_01 = (float) Math.PI/1800;
	static float DEG_05 = (float) Math.PI/360;
	static float DEG_1 = (float) Math.PI/180;
	static float DEG_5 = (float) Math.PI/36;
	static float DEG_15 = (float) Math.PI/12;
	static float DEG_30 = (float) Math.PI/6;
	static float DEG_45 = (float) Math.PI/4;
	static float DEG_60 = (float) Math.PI/3;
	static float DEG_90 = (float) Math.PI/2;
	static float DEG_180 = (float) Math.PI;
	static float DEG_360 = (float) Math.PI*2;

	//Radius allowances
	static float RADIUS_1 = 1.01f;
    
    //Constant variables
    static final float epsilon = 1e-08f;
    static final int VISITED_ARR_SIZE = 10;         			//Number of previous locations recorded

	//Early game estimation heuristics
	static final float MAP_SMALL = 40f;							//Max size of map considered 'small'
	static final float MAP_LARGE = 73f;							//Max size of map considered 'large'
	static final float INITIAL_BULLETS = 300f;					//Starting number of bullets

	//Movement finesse
    static final int MOVEMENT_GRANULARITY = 10;      			//Number of possible move directions
    static final int MOVEMENT_GRANULARITY_MICRO = 8;      		//Number of possible move directions for micro
    static final int BASHING_GRANULARITY = 15;                  //Number of angles around the targeted direction to force first before wall-following
	static final int SEARCH_GRANULARITY = 12;       			//Number of search directions
    static final int SENSOR_GRANULARITY = 72;                   //How many angles to scan around
    static final int SOLDIER_CHASING_GRANULARITY = 24;          //Lessen bytecode usage when chasing enemies...
    static final float SENSOR_SMOOTHENING_THRESHOLD = 0.001f;   //Smoothens 'bumps' in the sensor data
    static float MOVEMENT_SCANNING_RADIUS = 1.1f;               //Radius of dot scanned to move in
    static float MOVEMENT_SCANNING_DISTANCE = 2.1f;             //Distance away from center to scan in
	static final float TARGET_RELEASE_DIST = 9f;    			//This is a square distance. Assume you have reached your destination if your target falls within this distance
	static final float STUCK_THRESHOLD = 0.2f;      			//Threshold to call if unit is stuck
    static final int STUCK_THRESHOLD_TURNS = 9;                 //Number of turns spent going back and forth being stuck...
	static final int GARDENER_MOVE_GRANULARITY = 12;			//Number of times gardener will try random directions

	//Game-changing macro controls
	static final int MAX_STOCKPILE_BULLET = 500;    			//Maximum bullets in stockpile before donation occurs
	static final int DONATION_AMT = 50;             			//Amount donated each time
	static final int GAME_INITIALIZE_ROUND = 2;					//How many rounds before game is initialized
	static final int EARLY_GAME = 124;              			//How early is counted as 'early game'
	static final int MID_GAME = 500;							//When mid-game starts
	static final int LATE_GAME = 1800;							//Late game case
	static int THE_FINAL_PROBLEM = 2998;						//Last ditch effort
	static final float FOREST_DENSITY_THRESHOLD = 0.20f;		//% of area covered by trees in sense range
	static final int FARMER_MIN_SLOTS = 4;          			//Minimum number of trees that can be built before starting farm [1-6]
	static final int GARDENER_STUCK_TIMER = 17;					//After n turn, just spawn a lumberjack already!
	static final float OFFENSE_FACTOR = 3f; 					//Factor to prioritise offense / defense for scout micro [Higher => more offensive]

	//Early game build order
	static final int MAX_BUILD_ORDER = 3;						//Reserve how many slots in build queue
	static final int BUILD_ORDER_QUEUE = 100;					//Start of channel for build order

	//Spawning constants (Macro control)
	static final int SOLDIER_DELAY = 40;          				//Number of turns to spawn a new soldier
    static final int GARDENER_DELAY = 50;           			//Number of turns to spawn a new gardener
	static final int SCOUT_DELAY = 50; 							//Number of turns to spawn a new scout
	static final int TANK_DELAY = 20; 							//Number of turns to spawn a new tank

	static final float SCOUT_SPAWN_BASE_CHANCE = 0.90f;			//Base scout spawn chance
	static float SCOUT_SPAWN_CHANCE = SCOUT_SPAWN_BASE_CHANCE; 	//Initial chance to spawn a scout (set as base chance)
	static final float SPAWN_GARDENER_CHANCE = 1.1f;			//Chance to spawn a gardener
	static float SPAWN_GARDENER_DENSITY_THRESHOLD = 0.90f;		//% Area blocked around archon before it stops spawning gardeners

	static final int SCOUT_OBSOLETE_LIMIT = 2500;				//By which round the scout becomes obsolete

	//Micro constants
	static final float SOLDIER_MICRO_DIST = 33f; 				//Squared dist. Distance nearest enemy has to be to engage micro code
	static final float ARCHON_MICRO_DIST = 100.1f; 				//Squared dist. Distance nearest enemy has to be to engage micro code
	static final float SCOUT_MICRO_DIST = 64.3f;				//Squared dist. Distance nearest enemy has to be to engage micro code
	static final float LUMBERJACK_AVOIDANCE_RANGE = 5f; 		//How far to avoid lumberjacks
	static final float LUMBERJACK_AVOIDANCE_RANGE_ARCHON = 8f; 	//How far to avoid lumberjacks
	static final float LUMBERJACK_AVOIDANCE_RANGE_SCOUT = 4f;	//How far to avoid lumberjacks
	static final float PENTAD_EFFECTIVE_DISTANCE = 7.596f;		//(1/tan(7.5deg)) Such that at least 2 shots will hit a target with radius 1
    static final float BULLET_SENSE_RANGE = 5f; 				//The range at which bullets are sensed
    static final float SOLDIER_COMBAT_SENSE_RADIUS = 5f;        //Distance to sense friendly units at (soldier)
    static final float SCOUT_COMBAT_SENSE_RADIUS = 5f;          //Distance to sense friendly units at (scout)
    static final float POINT_BLANK_RANGE = 0.15f; //Distance to just shoot an enemy even if friendly fire will happen

	//Flocking behavior
	static float SPHERE_REPULSION = 3f;
	static float SPHERE_ATTRACTION = 6f;
	static float SPHERE_ALIGNMENT = 12f;

	//Surroundings sensing
	static TreeInfo[] SURROUNDING_TREES_OWN;
	static TreeInfo[] SURROUNDING_TREES_ENEMY;
	static TreeInfo[] SURROUNDING_TREES_NEUTRAL;
	static RobotInfo[] SURROUNDING_ROBOTS_OWN;
	static RobotInfo[] SURROUNDING_ROBOTS_ENEMY;
	static MapLocation ENEMY_ARCHON_LOCATION;
	static MapLocation MY_LOCATION;

	static ArrayList<Direction> HEXAGONAL_PATTERN = new ArrayList<Direction>(Arrays.asList(new Direction(0f), new Direction(DEG_60), new Direction (DEG_60*2), new Direction(DEG_180),
											new Direction(DEG_60*4), new Direction(DEG_60*5)));
    static float[] SENSOR_ANGLES = new float[SENSOR_GRANULARITY];
    static float[] LIGHTWEIGHT_SENSOR_ANGLES = new float[SOLDIER_CHASING_GRANULARITY];

    //Robot specific variables
    static boolean isSet;                           //Whether important local variables are set
    static int timeLived;                           //Time since spawn
    static int patience;                            //For movement
    static ArrayList<MapLocation> visitedArr;       //For movement
    static MapLocation target;                      //This is the position the robot will move to
    static boolean inCombat;                        //Is this robot in combat?
	static int ROBOT_SPAWN_ROUND = 0;

	//Archon specific variables
	static float ARCHON_SIGHT_AREA = 100*(float)Math.PI;
	static int gardenerTimer;
	static float ARCHON_JIGGLE_RADIUS = 3.2f;
	static boolean can_spawn_gardener;
	static Direction least_dense_direction;
	static Direction spawn_direction;
	static float est_size;
	static boolean solo_gardener_start = false;
	static float ARCHON_CLEARANCE_RADIUS = 4.05f;
	static float ARCHON_SIGHT_RADIUS = 10f;
	static float TEST_ON_MAP_RADIUS = 0.25f;
    
    //Gardener specific variables
	static float GARDENER_SIGHT_AREA = 49*(float)Math.PI;
    static boolean spawnedLumberjack;
    static boolean settledDown;
    static int resetTimer;
    static int soldierTimer;
    static int lumberjackTimer;
    static Direction moveDir = randomDirection();
	static Direction SPAWN_DIR = null;              //Location to start spawning soldiers/troops
	static int FARMER_TIMER_THRESHOLD = 0;
	static int GARDENER_TANK_THRESHOLD = 400;
	static float FARMER_CLEARANCE_RADIUS = 6f;	    //Distance to be away from other farmers before starting farm
	static float FARMER_ARCHON_DISTANCE = 5f;		//Distance to be away from archon before starting farm
    static int FARMER_MAX_TREES = 3;                //Max number of trees to plant within first few turns?
    static ArrayList<Direction> current_available_slots = new ArrayList<>();
    
    //Lumberjack specific variables
    static TreeInfo targetInfo;
    static boolean isInterruptable;                 //Can this lumberjack be interrupted to chop down some trees? true in scout mode, false otherwise

	//Soldier specific variables
	static final int SOLDIER_ALARM_RELEVANCE = 5;   			//Relevance of a soldier alarm
	static final float SOLDIER_SHOOT_RANGE = 64f;   			//This is a squared distance
    static MapLocation prevLocation = null;                     //Previous location
    static MapLocation centerPoint = null;                      //3rd location (average location based on past moves)
    static int staying_conut = 0;                               //How many turns spent around there
    static float movement_stuck_radius = 1.1f;                  //Radius whereby getting stuck is counted
    static boolean am_stuck = false;                            //I am stuck where I am...
    static boolean search_dir = true;                           //Right/Left bot, true == Right bot

	//Broadcast channel statics
	static final int BORDER_LEFT = 0;
	static final int BORDER_TOP = 1;
	static final int BORDER_RIGHT = 2;
	static final int BORDER_BOTTOM = 3;
	static final int SPOTTED_TURN = 10;
	static final int SPOTTED_LOCATION = 11;
	static final int SPOTTED_ENEMY_TYPE = 12;
	static final int SPAWN_TREES = 410;
	static final int SPAWN_SCOUT = 411;
	static final int SPAWN_SOLDIERS = 412;
	static final int SPAWN_TANKS = 413;
	static final int SPAWN_LUMBERJACKS = 414;

	static final int EARLY_SCOUT = 421;

	static final int EARLY_SOLDIER = 431;
	static final int SCOUT_RUSH_DETECTED = 432;
	static final int SCOUT_RUSHED = 433;

	static final int SPAWN_FARMER = 440;
	static final int SPAWN_FARMER_THRESHOLD = 441;
	static final int DENSE_FOREST_DETECTED = 442;

	static final int RESERVE_BULLETS = 600;

	//Massive trigo optimization
    static final double[] SIN_VALUES = {0.0, 0.01745240643728351, 0.03489949670250097, 0.05233595624294383, 0.0697564737441253, 0.08715574274765817, 0.10452846326765346, 0.12186934340514748, 0.13917310096006544, 0.15643446504023087, 0.17364817766693033, 0.1908089953765448, 0.20791169081775931, 0.22495105434386498, 0.24192189559966773, 0.25881904510252074, 0.27563735581699916, 0.2923717047227367, 0.3090169943749474, 0.3255681544571567, 0.3420201433256687, 0.35836794954530027, 0.374606593415912, 0.3907311284892737, 0.40673664307580015, 0.42261826174069944, 0.4383711467890774, 0.45399049973954675, 0.4694715627858908, 0.48480962024633706, 0.49999999999999994, 0.5150380749100542, 0.5299192642332049, 0.544639035015027, 0.5591929034707468, 0.573576436351046, 0.5877852522924731, 0.6018150231520483, 0.6156614753256583, 0.6293203910498375, 0.6427876096865393, 0.6560590289905073, 0.6691306063588582, 0.6819983600624985, 0.6946583704589973, 0.7071067811865475, 0.7193398003386511, 0.7313537016191705, 0.7431448254773941, 0.7547095802227719, 0.766044443118978, 0.7771459614569708, 0.7880107536067219, 0.7986355100472928, 0.8090169943749475, 0.8191520442889918, 0.8290375725550417, 0.8386705679454239, 0.8480480961564261, 0.8571673007021122, 0.8660254037844386, 0.8746197071393957, 0.8829475928589269, 0.8910065241883678, 0.898794046299167, 0.9063077870366499, 0.9135454576426009, 0.9205048534524404, 0.9271838545667873, 0.9335804264972017, 0.9396926207859083, 0.9455185755993167, 0.9510565162951535, 0.9563047559630354, 0.9612616959383189, 0.9659258262890683, 0.9702957262759965, 0.9743700647852352, 0.9781476007338057, 0.981627183447664, 0.984807753012208, 0.9876883405951378, 0.9902680687415704, 0.992546151641322, 0.9945218953682733, 0.9961946980917455, 0.9975640502598242, 0.9986295347545738, 0.9993908270190958, 0.9998476951563913, 1.0, 0.9998476951563913, 0.9993908270190958, 0.9986295347545738, 0.9975640502598242, 0.9961946980917455, 0.9945218953682734, 0.9925461516413221, 0.9902680687415704, 0.9876883405951377, 0.984807753012208, 0.981627183447664, 0.9781476007338057, 0.9743700647852352, 0.9702957262759965, 0.9659258262890683, 0.9612616959383189, 0.9563047559630355, 0.9510565162951536, 0.9455185755993168, 0.9396926207859084, 0.9335804264972017, 0.9271838545667874, 0.9205048534524404, 0.913545457642601, 0.90630778703665, 0.8987940462991669, 0.8910065241883679, 0.8829475928589271, 0.8746197071393959, 0.8660254037844387, 0.8571673007021123, 0.8480480961564261, 0.838670567945424, 0.8290375725550417, 0.819152044288992, 0.8090169943749475, 0.7986355100472927, 0.788010753606722, 0.777145961456971, 0.766044443118978, 0.7547095802227721, 0.7431448254773945, 0.7313537016191706, 0.7193398003386511, 0.7071067811865476, 0.6946583704589975, 0.6819983600624985, 0.669130606358858, 0.6560590289905073, 0.6427876096865395, 0.6293203910498374, 0.6156614753256584, 0.6018150231520486, 0.5877852522924732, 0.5735764363510459, 0.5591929034707469, 0.5446390350150273, 0.5299192642332049, 0.5150380749100544, 0.49999999999999994, 0.48480962024633717, 0.4694715627858907, 0.45399049973954686, 0.4383711467890777, 0.4226182617406995, 0.40673664307580004, 0.39073112848927377, 0.37460659341591224, 0.35836794954530066, 0.3420201433256689, 0.3255681544571566, 0.3090169943749475, 0.29237170472273705, 0.2756373558169992, 0.258819045102521, 0.24192189559966773, 0.2249510543438652, 0.20791169081775931, 0.19080899537654497, 0.1736481776669307, 0.15643446504023098, 0.13917310096006533, 0.12186934340514755, 0.10452846326765373, 0.0871557427476582, 0.06975647374412552, 0.05233595624294425, 0.03489949670250114, 0.01745240643728344, 1.2246467991473532e-16, -0.017452406437283192, -0.0348994967025009, -0.052335956242943564, -0.06975647374412483, -0.08715574274765794, -0.1045284632676535, -0.12186934340514774, -0.13917310096006552, -0.15643446504023073, -0.17364817766693047, -0.19080899537654472, -0.20791169081775907, -0.22495105434386498, -0.2419218955996675, -0.25881904510252035, -0.2756373558169986, -0.29237170472273677, -0.30901699437494773, -0.32556815445715676, -0.34202014332566866, -0.35836794954530043, -0.374606593415912, -0.39073112848927355, -0.4067366430757998, -0.4226182617406993, -0.43837114678907707, -0.45399049973954625, -0.4694715627858905, -0.48480962024633734, -0.5000000000000001, -0.5150380749100542, -0.5299192642332048, -0.5446390350150271, -0.5591929034707467, -0.5735764363510457, -0.587785252292473, -0.601815023152048, -0.6156614753256578, -0.6293203910498373, -0.6427876096865393, -0.6560590289905074, -0.6691306063588582, -0.6819983600624984, -0.6946583704589973, -0.7071067811865475, -0.7193398003386509, -0.7313537016191705, -0.743144825477394, -0.7547095802227717, -0.7660444431189779, -0.7771459614569711, -0.7880107536067221, -0.7986355100472928, -0.8090169943749473, -0.8191520442889916, -0.8290375725550414, -0.838670567945424, -0.848048096156426, -0.8571673007021121, -0.8660254037844384, -0.8746197071393955, -0.882947592858927, -0.8910065241883678, -0.8987940462991668, -0.9063077870366502, -0.913545457642601, -0.9205048534524403, -0.9271838545667873, -0.9335804264972016, -0.9396926207859082, -0.9455185755993168, -0.9510565162951535, -0.9563047559630353, -0.961261695938319, -0.9659258262890683, -0.9702957262759965, -0.9743700647852351, -0.9781476007338056, -0.981627183447664, -0.984807753012208, -0.9876883405951377, -0.9902680687415703, -0.992546151641322, -0.9945218953682733, -0.9961946980917455, -0.9975640502598242, -0.9986295347545739, -0.9993908270190958, -0.9998476951563913, -1.0, -0.9998476951563913, -0.9993908270190958, -0.9986295347545739, -0.9975640502598243, -0.9961946980917455, -0.9945218953682733, -0.992546151641322, -0.9902680687415704, -0.9876883405951378, -0.9848077530122081, -0.9816271834476641, -0.9781476007338056, -0.9743700647852352, -0.9702957262759966, -0.9659258262890684, -0.961261695938319, -0.9563047559630354, -0.9510565162951536, -0.945518575599317, -0.9396926207859083, -0.9335804264972017, -0.9271838545667874, -0.9205048534524405, -0.9135454576426011, -0.9063077870366503, -0.898794046299167, -0.891006524188368, -0.8829475928589271, -0.8746197071393956, -0.8660254037844386, -0.8571673007021123, -0.8480480961564261, -0.8386705679454243, -0.8290375725550416, -0.8191520442889918, -0.8090169943749476, -0.798635510047293, -0.7880107536067223, -0.7771459614569713, -0.7660444431189781, -0.7547095802227722, -0.743144825477394, -0.7313537016191703, -0.7193398003386511, -0.7071067811865477, -0.6946583704589976, -0.6819983600624989, -0.6691306063588588, -0.6560590289905074, -0.6427876096865396, -0.6293203910498372, -0.6156614753256582, -0.6018150231520483, -0.5877852522924734, -0.5735764363510465, -0.5591929034707473, -0.544639035015027, -0.529919264233205, -0.5150380749100545, -0.5000000000000004, -0.48480962024633767, -0.4694715627858908, -0.45399049973954697, -0.4383711467890778, -0.4226182617406992, -0.40673664307580015, -0.3907311284892739, -0.37460659341591235, -0.35836794954530077, -0.34202014332566943, -0.3255681544571567, -0.3090169943749477, -0.29237170472273716, -0.27563735581699894, -0.2588190451025207, -0.24192189559966787, -0.22495105434386534, -0.20791169081775987, -0.19080899537654467, -0.1736481776669304, -0.1564344650402311, -0.13917310096006588, -0.12186934340514811, -0.1045284632676543, -0.08715574274765832, -0.06975647374412564, -0.05233595624294348, -0.034899496702500823, -0.01745240643728356};

    /*
     * Broadcast Channels:
     * 0 => left border, 1 => top border, 2 => right border, 3 => bottom border
     * 10 => turn enemy scouted, 11 => enemy scouted position [Only updated when in combat]
     *
     * Shared Array SOP
	 * [100-109] Team Orders
	 * [200-209] Team Order Data
	 * [0-300] Team Robot Status
	 *
	 * Squad Setup
	 * [10-99] Team Members
	 * [110-199] Member Data
	 * [210-299] Member Keep Alive Signal
	 *
	 * Ops Orders
	 * [400-410] Reserved
	 * [410] Spawn Trees
	 * [411] Spawn Scout
	 * [412] Spawn Soldiers
	 * [413] Spawn Tanks
	 * [414] Spawn Lumberjacks
	 * [420] Offensive Posture
	 * [421] Early game scout rush		[0] - Deactivated	[1] - Activated (Allows gardener to build scouts early game)
	 * [430] Defensive Posture
	 * [431] Early Soldier Spawn		[0] - Deactivated	[1] - Activated (Allows gardener to build soldiers early game)
	 * [432] Enemy scout rush detected!
	 * [440] Farming mode
	 * [441] Farmer Spawn Threshold
	 * [442] Dense Forest detected
	 *
	 * Archon Location
	 * [501-502] Archon location
	 * [500] Archon ID
	 * [511-512] Archon location
	 * [510] Archon ID
	 * [521-522] Archon location
	 * [520] Archon ID
	 * [531-532] Archon location
	 * [530] Archon ID
	 * [541-542] Archon location
	 * [540] Archon ID
	 *
	 * Farmers
	 * [551-552] Farmer location
	 * [550] Farmer ID
	 * [553] Farmer Keep Alive
	 * ... etc
	 *
	 * ENEMY AT THE GATES
	 * [600-699]
	 * [600] Request for bullet reservation (archon pls dun spawn garderners thx)
	 * [601] Heartbeat for request
	 *
	 * Gardeners Counter
	 * [700-799] Keep Alive heartbeat
	 *
	 * Troops Counter
	 * [800-899] Keep Alive heartbeat
	 *
	 * Reserved Channels for future use
	 * [900-999]
     *
     */

    /*
     * General TODO:
     * 1. Find a way to determine map density then choose the appropriate bot
     * 2. Improve scout micro to scout out broadcasting enemy robots (hunter killer behavior)
     * 3. OOP the code structure :D
     */

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        //Global Initialization
        RobotPlayer.rc = rc;
		ROBOT_SPAWN_ROUND = rc.getRoundNum();
		THE_FINAL_PROBLEM = rc.getRoundLimit()-3;
		ENEMY_ARCHON_LOCATION = rc.getInitialArchonLocations(rc.getTeam().opponent())[0];

        //Set up sensor angle array
        for (int i=0;i<SENSOR_GRANULARITY;i++){
            SENSOR_ANGLES[i] = i*(DEG_360/SENSOR_GRANULARITY);
        }
        for (int i=0;i<SOLDIER_CHASING_GRANULARITY;i++){
            LIGHTWEIGHT_SENSOR_ANGLES[i] = i*(DEG_360/SOLDIER_CHASING_GRANULARITY);
        }

		//Robot Specific Initializations
		try{
			switch (rc.getType()) {
				case ARCHON:
                    //Setup scanner range based on robot type
                    MOVEMENT_SCANNING_DISTANCE = 4.1f;
                    MOVEMENT_SCANNING_RADIUS = 2.1f;

                    //Use archon positions to find rough size of map then set early-game strategy
					MY_LOCATION = rc.getLocation();
					est_size = ENEMY_ARCHON_LOCATION.distanceTo(MY_LOCATION);

					//Estimate which corner archon is in


					if (est_size < MAP_SMALL){
						rc.broadcast(EARLY_SOLDIER,1);	//Enables early game soldier build
					}
					else if (est_size < MAP_LARGE){
						rc.broadcast(EARLY_SOLDIER,1);	//Enables early game soldier build
						rc.broadcast(EARLY_SCOUT,1);	//Enables early game scout rush
					}
					else {
						rc.broadcast(EARLY_SOLDIER,1);	//Enables early game soldier build
						rc.broadcast(EARLY_SCOUT,1);	//Enables early game scout rush
					}

					//Setup build order
					//Check forest density
					float area_trees = 0f;
					int[] build_list = null;
                    int[] sco_sco_sco = {robotType(RobotType.SCOUT),robotType(RobotType.SCOUT),robotType(RobotType.SCOUT)};
					int[] sco_sco_lum = {robotType(RobotType.SCOUT),robotType(RobotType.SCOUT),robotType(RobotType.LUMBERJACK)};
                    int[] sco_lum_sco = {robotType(RobotType.SCOUT),robotType(RobotType.LUMBERJACK),robotType(RobotType.SCOUT)};
                    int[] sco_lum_sol = {robotType(RobotType.SCOUT),robotType(RobotType.LUMBERJACK),robotType(RobotType.SOLDIER)};
                    int[] sco_sol_lum = {robotType(RobotType.SCOUT),robotType(RobotType.SOLDIER),robotType(RobotType.LUMBERJACK)};
					int[] sol_sco_sco = {robotType(RobotType.SOLDIER),robotType(RobotType.SCOUT),robotType(RobotType.SCOUT)};
					System.out.println("Surrounding foilage density: "+Float.toString(getDensity_trees(MY_LOCATION, ARCHON_SIGHT_RADIUS)));
					if (getDensity_trees(MY_LOCATION, ARCHON_SIGHT_RADIUS) < FOREST_DENSITY_THRESHOLD){
					    build_list = sco_sco_lum;
					    if (est_size < MAP_SMALL){
                            build_list = sco_sol_lum;
                        }
                        else if (est_size > MAP_LARGE){
                            build_list = sco_sco_sco;
                        }
					}
					else{
                        System.out.println("DENSE MAP DETECTED");
						build_list = sco_lum_sco;
					}
					if (build_list!=null) {
						rc.broadcast(BUILD_ORDER_QUEUE, build_list[0]);
						rc.broadcast(BUILD_ORDER_QUEUE + 1, build_list[1]);
						rc.broadcast(BUILD_ORDER_QUEUE + 2, build_list[2]);
					}
                    System.out.println("Build order: " + Integer.toString(build_list[0]) +" "+ Integer.toString(build_list[1]) +" "+ Integer.toString(build_list[2]));

                    /*int[] build_list = {1,3,1};
                    rc.broadcast(BUILD_ORDER_QUEUE, build_list[0]);
                    rc.broadcast(BUILD_ORDER_QUEUE + 1, build_list[1]);
                    rc.broadcast(BUILD_ORDER_QUEUE + 2, build_list[2]);*/

					break;
				case GARDENER:
                    MOVEMENT_SCANNING_DISTANCE = 3.1f;
                    MOVEMENT_SCANNING_RADIUS = 2.1f;
					SPHERE_REPULSION = 4.04f;
					SPHERE_ATTRACTION = 6.7f;
					break;
				case SOLDIER:
					break;
				case LUMBERJACK:
					break;
				case SCOUT:
					break;
				case TANK:
                    //Setup scanner range based on robot type
                    MOVEMENT_SCANNING_DISTANCE = 4.1f;
                    MOVEMENT_SCANNING_RADIUS = 2.1f;
					break;
			}
		}
		catch (Exception e){ //This is for debugging => The system doesn't catch these errors properly
			e.printStackTrace();
		}

        // Here, we've separated the controls into a different method for each RobotType.
        // You can add the missing ones or rewrite this into your own control structure.
        while (true){
        	try{
		        //Always be on the lookout for neutral trees - might come in handy for pathing later on too...
                SURROUNDING_ROBOTS_ENEMY = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                SURROUNDING_TREES_NEUTRAL = rc.senseNearbyTrees(3f, Team.NEUTRAL);
                MY_LOCATION = rc.getLocation();

                if (rc.getRoundNum() < EARLY_GAME) {
                    for (RobotInfo r : SURROUNDING_ROBOTS_ENEMY) {
                        if (r.type == RobotType.SCOUT){
                            System.out.println("I have detected a scout rushing us!!");
							rc.broadcast(SCOUT_RUSH_DETECTED, 1);
                        }
                    }
                }

        		switch (rc.getType()) {
					case ARCHON:
		                runArchon();
		                break;
		            case GARDENER:
                        SURROUNDING_TREES_OWN = rc.senseNearbyTrees(2.7f, rc.getTeam());
                        SURROUNDING_TREES_NEUTRAL = rc.senseNearbyTrees(-1, Team.NEUTRAL);
		            	//Macro game adaptations to perceived map size && round number
						if (rc.getRoundNum() > EARLY_GAME){
		            		//Decaying chance to spawn scouts ==> late game less scouts
							System.out.println("Scout spawn chance:");
		            		SCOUT_SPAWN_CHANCE *= (float)(SCOUT_OBSOLETE_LIMIT-rc.getRoundNum()+(0.95f*(rc.getRoundNum()-EARLY_GAME)))/(float)(SCOUT_OBSOLETE_LIMIT-EARLY_GAME);
		            		System.out.println(SCOUT_SPAWN_CHANCE);
						}
		                runGardener();
		                break;
		            case SOLDIER:
                        System.out.println("Robot overhead cost: "+Integer.toString(Clock.getBytecodeNum()));
		                runSoldier();
		                System.out.println("Before computing stuck: "+Integer.toString(Clock.getBytecodeNum()));
                        if (prevLocation == null){
                            prevLocation = MY_LOCATION;
                            MY_LOCATION = rc.getLocation();
                            staying_conut = 0;
                            am_stuck = false;
                        }
                        else{
                            //rc.setIndicatorDot(prevLocation, 255, 100, 100);
                            //rc.setIndicatorDot(MY_LOCATION, 100,255, 125);
                            centerPoint = MY_LOCATION.add(MY_LOCATION.directionTo(prevLocation), MY_LOCATION.distanceTo(prevLocation)/2);
                            prevLocation = MY_LOCATION;
                            MY_LOCATION = rc.getLocation();
                            System.out.println("Evaluated previous distance: "+Float.toString(MY_LOCATION.distanceTo(centerPoint)));
                            if (MY_LOCATION.distanceTo(centerPoint) < movement_stuck_radius && !inCombat){
                                //rc.setIndicatorDot(centerPoint, 255,0, 0);
                                staying_conut++;
                            }
                            else{
                                staying_conut = 0;
                                //rc.setIndicatorDot(centerPoint, 100, 100, 255);
                            }
                        }
                        if (staying_conut > STUCK_THRESHOLD_TURNS){
                            System.out.println("Oh oops, I'm STUCK!!!");
                            am_stuck = true;
                        }
                        System.out.println("After computing stuck: "+Integer.toString(Clock.getBytecodeNum()));
		                break;
		            case LUMBERJACK:
                        SURROUNDING_ROBOTS_OWN = rc.senseNearbyRobots(-1, rc.getTeam());
                        SURROUNDING_ROBOTS_ENEMY = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                        SURROUNDING_TREES_OWN = rc.senseNearbyTrees(2.7f, rc.getTeam());
                        SURROUNDING_TREES_ENEMY = rc.senseNearbyTrees(-1, rc.getTeam().opponent());
                        SURROUNDING_TREES_NEUTRAL = rc.senseNearbyTrees(-1, Team.NEUTRAL);
		            	runLumberjack();
		            	break;
		            case SCOUT:
                        SURROUNDING_ROBOTS_OWN = rc.senseNearbyRobots(-1, rc.getTeam());
                        SURROUNDING_ROBOTS_ENEMY = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                        SURROUNDING_TREES_OWN = rc.senseNearbyTrees(2.7f, rc.getTeam());
                        SURROUNDING_TREES_ENEMY = rc.senseNearbyTrees(-1, rc.getTeam().opponent());
		            	runScout();
		            	break;
		            case TANK:
                        SURROUNDING_ROBOTS_OWN = rc.senseNearbyRobots(-1, rc.getTeam());
                        SURROUNDING_ROBOTS_ENEMY = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                        SURROUNDING_TREES_OWN = rc.senseNearbyTrees(2.7f, rc.getTeam());
                        SURROUNDING_TREES_ENEMY = rc.senseNearbyTrees(-1, rc.getTeam().opponent());
						runTank();
		            	break;
		        }

		        //Shake trees in vicinity
				if (SURROUNDING_TREES_NEUTRAL.length > 0){
        			if (rc.canShake(SURROUNDING_TREES_NEUTRAL[0].ID)) rc.shake(SURROUNDING_TREES_NEUTRAL[0].ID);
				}

		        //Donate
		        donate();

				//Final few rounds, cash everything in!!!
                if (rc.getRoundNum() > THE_FINAL_PROBLEM){
                    rc.donate((float)Math.floor(rc.getTeamBullets()/10)*10);
                }

		        //Give up the turn
		        Clock.yield();
        	}
        	catch (Exception e){ //This is for debugging => The system doesn't catch these errors properly
        		e.printStackTrace();
        	}
        }
	}

    static void runArchon(){
    	boolean can_spawn = false;
    	Random rand = new Random(rc.getID());
		can_spawn_gardener = true;
		least_dense_direction = null;
		if (!isSet) {
			visitedArr = new ArrayList<MapLocation>();
			isSet = true;
			targetInfo = null;
			patience = 0;
		}

		try {
			//Only spawn 1 gardener
			if (rc.getRoundNum() < EARLY_GAME && solo_gardener_start){
				System.out.println("Game initializing...");
				System.out.println("Current bullets left: "+Float.toString(rc.getTeamBullets()));
				if (rc.getTeamBullets() < INITIAL_BULLETS-80) can_spawn_gardener = false;
			}
			//Check for scout rush in progress
			if (rc.getRoundNum() < EARLY_GAME) {
				if (rc.readBroadcast(SCOUT_RUSH_DETECTED) == 1) can_spawn_gardener = false;
				if (rc.getRoundNum() > GAME_INITIALIZE_ROUND) {
					for (int i = 0; i < MAX_BUILD_ORDER; i++) {
						if (rc.readBroadcast(i + BUILD_ORDER_QUEUE) != 0) can_spawn_gardener = false;
					}
				}
			}
			//Check whether there's request to reserve bullets
			int gardener_need_bullet = rc.readBroadcast(RESERVE_BULLETS);
			int heartbeat = rc.readBroadcast(RESERVE_BULLETS+1);
			if (gardener_need_bullet == 1 && heartbeat > rc.getRoundNum()-3){
				can_spawn_gardener = false;
			}
		} catch (Exception e){
			e.printStackTrace();
		}

    	if (!isSet){
    		gardenerTimer = 0;
    		isSet = true;
    	}

		//Calls for help when enemies are nearby
		if (SURROUNDING_ROBOTS_ENEMY.length > 0){
			System.out.println("ENEMY AT THE GATES!!!");
			spotEnemy(SURROUNDING_ROBOTS_ENEMY[0]);
		}

		//Look for nearby enemies or bullets
		BulletInfo [] nearbyBullets = rc.senseNearbyBullets(-1);
		float nearestdist = 1000000f;
		RobotInfo nearestEnemy = null;
		if (SURROUNDING_ROBOTS_ENEMY.length > 0) {
            nearestEnemy = SURROUNDING_ROBOTS_ENEMY[0];
            nearestdist = MY_LOCATION.distanceSquaredTo(nearestEnemy.location);
            if (nearestdist <= ARCHON_MICRO_DIST || nearbyBullets.length > 0) {
                archonCombatMove(nearestEnemy, SURROUNDING_ROBOTS_ENEMY, nearbyBullets);
            }
        }

		//Check whether current position can spawn units, else move
		try {
			for (int i = 0; i < SEARCH_GRANULARITY; i++) {
				if (can_spawn) break;
				Direction cDir = new Direction(i * (DEG_360/SEARCH_GRANULARITY));
				if (rc.canBuildRobot(RobotType.GARDENER, cDir)) {
					can_spawn = true;
				}
			}
			if (!can_spawn) {
				if (!rc.hasMoved()) {
					//Get most spacious direction
					float density = 999999f;
					float offset = rand.nextInt(360)*DEG_1;
					Direction least_dense_direction = null;
					for(int i=0;i<SEARCH_GRANULARITY;i++) {
						Direction cDir = new Direction(absRad(i * (DEG_360/SEARCH_GRANULARITY)+offset));
						float eval = getDensity(MY_LOCATION.add(cDir,MOVEMENT_SCANNING_DISTANCE),MOVEMENT_SCANNING_RADIUS);
						if (!rc.onTheMap(MY_LOCATION.add(cDir,MOVEMENT_SCANNING_DISTANCE), TEST_ON_MAP_RADIUS)) continue;
						if(eval<density){
							density = eval;
							least_dense_direction = cDir;
						}
					}

					if(least_dense_direction != null) {
						if (rc.canMove(least_dense_direction)) rc.move(least_dense_direction);
					}
				}
			}
			//todo experimental code!!! take out when submitting if it fails
			if(!isSpreadOut(MY_LOCATION, ARCHON_CLEARANCE_RADIUS)){
                RobotInfo[] nearby_farmers = rc.senseNearbyRobots(ARCHON_CLEARANCE_RADIUS, rc.getTeam());
                for (RobotInfo r:nearby_farmers){
                    if (!rc.hasMoved()) {
                        if (r.type == RobotType.GARDENER) {
                            Direction toFarmerOpposite = MY_LOCATION.directionTo(r.location).opposite();
                            for (int i = 0; i < MOVEMENT_GRANULARITY; i++) {
                                Direction tryDir = toFarmerOpposite.rotateRightRads(DEG_1 * 3 * i);
                                if (rc.canMove(tryDir)) {
                                    rc.move(tryDir);
                                    break;
                                }
                                else{
                                    tryDir = toFarmerOpposite.rotateLeftRads(DEG_1 * 3 * i);
                                    if (rc.canMove(tryDir)) {
                                        rc.move(tryDir);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
		} catch (Exception e){
    		e.printStackTrace();
		}

        System.out.println("Am I skipping turns? :O "+Integer.toString(Clock.getBytecodeNum()));

    	//Spawn Code
    	if (rc.getRoundNum() >= gardenerTimer && ((rc.getRoundNum() > 3)?(rand.nextFloat() < SPAWN_GARDENER_CHANCE):true) && getDensity_robots(MY_LOCATION,ARCHON_CLEARANCE_RADIUS)<SPAWN_GARDENER_DENSITY_THRESHOLD && can_spawn_gardener){

    	    try {
				//Attempt to build gardener
				Direction[] build_locations = new Direction[SEARCH_GRANULARITY];
				int conut = 0;
				if (rc.isBuildReady()) {
					//Search for location to build gardener
					float offset = rand.nextInt(360)*DEG_1;
					for(int i=0;i<SEARCH_GRANULARITY;i++) {
						Direction cDir = new Direction(absRad(i * (DEG_360/SEARCH_GRANULARITY)+offset));
						if (rc.canHireGardener(cDir)) {
							build_locations[conut] = cDir;
							conut++;
						}
					}
				}

				//Get most spacious direction
				float density = 999999f;
				Direction spawn_direction = null;
				for(int i=0;i<conut;i++){
					float eval = getDensity(MY_LOCATION.add(build_locations[i],MOVEMENT_SCANNING_DISTANCE),MOVEMENT_SCANNING_RADIUS);
                    if (!rc.onTheMap(MY_LOCATION.add(build_locations[i],MOVEMENT_SCANNING_DISTANCE), TEST_ON_MAP_RADIUS)) continue;
					if(eval<density){
						density = eval;
						spawn_direction = build_locations[i];
					}
				}
				if(spawn_direction != null) {
					System.out.println("Hired Gardener in direction:");
					System.out.println(spawn_direction.radians);
					System.out.println(density);
					rc.hireGardener(spawn_direction);
					gardenerTimer = rc.getRoundNum() + GARDENER_DELAY;
				}

    			if (rc.getRoundNum() <= 5){
	    			//Update known map bounds
	    			rc.broadcast(0, 0);
	    			rc.broadcast(1,  0);
	    			rc.broadcast(2, 600);
	    			rc.broadcast(3, 600);
    			}
			} catch (GameActionException e) {
				e.printStackTrace();
			}
    	}
    }

	static void runGardener(){
		Random rand = new Random(rc.getID());
		boolean emergency = false;

		//Debug settling down
		System.out.println("Have I settled? "+Boolean.toString(settledDown));
		System.out.println("Max slots around me: "+Integer.toString(Math.max(FARMER_MIN_SLOTS-SURROUNDING_TREES_OWN.length,1)));

		//Activate emergency mode when there's enemies about...
		if (SURROUNDING_ROBOTS_ENEMY.length > 0){
			System.out.println("ENEMY AT THE GATES!!!");
			spotEnemy(SURROUNDING_ROBOTS_ENEMY[0]);
			emergency = true;
		}

		System.out.println("SCANNED FOR ENEMIES");

		//Whether robot variables are set
		if (!isSet){
			//System.out.println("Hi I'm a gardener!");
			timeLived = 0;
			visitedArr = new ArrayList<MapLocation>();
			isSet = true;
			patience = 0;
			spawnedLumberjack = false;
			resetTimer = 10000; //Set an arbitrarily large value
			targetInfo = null;
			soldierTimer = 200 + rc.getRoundNum();//When to spawn the next soldier
			settledDown = false; //Will be set to true if a suitable space can be found
		}
		else{
			timeLived += 1;
		}

        try {
            if (settledDown) current_available_slots = getBuildSlots(MY_LOCATION, Math.max(FARMER_MIN_SLOTS-SURROUNDING_TREES_OWN.length,1));
			else{
				if (!rc.isCircleOccupiedExceptByThisRobot(MY_LOCATION, 3.01f) && rc.onTheMap(MY_LOCATION, 3f)) current_available_slots = HEXAGONAL_PATTERN;
				else current_available_slots = getBuildSlots(MY_LOCATION, FARMER_MIN_SLOTS);
			}
			//if (settledDown) current_available_slots = getBuildSlots_exhaustive(MY_LOCATION, 1, Math.max(FARMER_MIN_SLOTS-SURROUNDING_TREES_NEUTRAL.length,1));
            //else current_available_slots = getBuildSlots_exhaustive(MY_LOCATION, FARMER_MIN_SLOTS, Math.max(FARMER_MIN_SLOTS-SURROUNDING_TREES_NEUTRAL.length,1));

			//Debug bytecode costs
			System.out.println("Bytes used to compute build locations: "+Integer.toString(Clock.getBytecodeNum()));

			if (current_available_slots.size() > 0) {
                //Get most spacious direction
                float density = 999999f;
                int spawn_direction = -1;
                System.out.println("Total build slots: " + Integer.toString(current_available_slots.size()));
                for (int i = 0; i < current_available_slots.size(); i++) {
                    System.out.println("Available build slot at: " + Float.toString(current_available_slots.get(i).radians));
                    float eval = getDensity(MY_LOCATION.add(current_available_slots.get(i), MOVEMENT_SCANNING_DISTANCE), MOVEMENT_SCANNING_RADIUS);
                    if (eval < density) {
                        density = eval;
                        spawn_direction = i;
                    }
                }
                if (spawn_direction != -1) {
                    SPAWN_DIR = current_available_slots.get(spawn_direction);
                    current_available_slots.remove(spawn_direction);
                }
            }
            else if (SURROUNDING_TREES_OWN.length > 4){
				//Quickly reserve a spawn location before tree is planted!!!
				Direction cDir = MY_LOCATION.directionTo(SURROUNDING_TREES_OWN[0].location);
				for (int i = 0; i < 5; i++) {
					cDir = new Direction(absRad(cDir.radians + DEG_60));
					if (rc.canBuildRobot(RobotType.SCOUT, cDir)) SPAWN_DIR = cDir;
				}
			}
        } catch (Exception e) {
            System.out.println("Gardener fails to get surrounding build slots");
            e.printStackTrace();
        }

        //Debug for spawn direction
		if (SPAWN_DIR != null) System.out.println("SPAWN_DIR: "+Float.toString(SPAWN_DIR.radians));

		//Check for early game starts
        try {
            if (SPAWN_DIR != null) {
                //Follow build order
                for (int i=0;i<MAX_BUILD_ORDER;i++){
                    int cur_build = rc.readBroadcast(i+BUILD_ORDER_QUEUE);
                    System.out.println("Current requested robot: "+Integer.toString(cur_build));
                    RobotType requested_robot = robotType(cur_build);
                    if (requested_robot != null){
                        if (rc.canBuildRobot(requested_robot, SPAWN_DIR)){
                            rc.buildRobot(requested_robot, SPAWN_DIR);
                            rc.broadcast(i+BUILD_ORDER_QUEUE, 0);
                            System.out.println("Built robot: "+Integer.toString(cur_build)+" as part of build queue");
                        }
                    }
                    if (cur_build != 0) break;
                }
            }
            else {
                //Force out a spawn direction
                for (int i=0;i<SENSOR_GRANULARITY;i++){
                    Direction cDir = new Direction(i*DEG_360/SENSOR_GRANULARITY);
                    if (rc.canBuildRobot(RobotType.SCOUT, cDir)){
                        SPAWN_DIR = cDir;
                        break;
                    }
                }
                if (SPAWN_DIR != null) {
                    for (int i = 0; i < MAX_BUILD_ORDER; i++) {
                        int cur_build = rc.readBroadcast(BUILD_ORDER_QUEUE);
                        System.out.println("Current requested robot: "+Integer.toString(cur_build));
                        RobotType requested_robot = robotType(cur_build);
                        if (requested_robot != null) {
                            if (rc.canBuildRobot(requested_robot, SPAWN_DIR)) {
                                rc.buildRobot(requested_robot, SPAWN_DIR);
                                rc.broadcast(i + BUILD_ORDER_QUEUE, 0);
                                System.out.println("Built robot: "+Integer.toString(cur_build)+" as part of build queue");
                            }
                        }
                        if (cur_build != 0) break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

		if (current_available_slots.size() > 0 && isSpreadOut(MY_LOCATION, FARMER_CLEARANCE_RADIUS)) settledDown = true;

		//Debug bytecode costs
		System.out.println("Bytes thus far: "+Integer.toString(Clock.getBytecodeNum()));

		if (!settledDown){
			//Try to spawn a lumberjack
			//Check if there are trees around
			try {
				if (SPAWN_DIR != null) {
					if (SURROUNDING_ROBOTS_ENEMY.length > 0) {
						spawnUnit(RobotType.SOLDIER, SPAWN_DIR);
					} else if (rc.getRoundNum() < EARLY_GAME) {
						if (!spawnedLumberjack) {
							spawnUnit(RobotType.LUMBERJACK, SPAWN_DIR);
							spawnedLumberjack = true;
						}
					} else {
						spawnUnit(RobotType.LUMBERJACK, SPAWN_DIR);
					}
				}
				else if (rc.getRoundNum() - GARDENER_STUCK_TIMER > ROBOT_SPAWN_ROUND) {
					for (int i = 0; i < SEARCH_GRANULARITY; i++) {
						Direction cDir = new Direction(i * DEG_360/SEARCH_GRANULARITY);
						spawnUnit(RobotType.LUMBERJACK, cDir);
					}
				}

				//Look for a space with a circle of radius 3
				if (target == null) target = ENEMY_ARCHON_LOCATION;
				gardenerMove(GARDENER_MOVE_GRANULARITY);

			} catch (Exception e){
				e.printStackTrace();
			}

		}
		else{
			//System.out.println("Settled Down");
			//Try to spawn a lumberjack when there are trees around >.<
			if (SPAWN_DIR != null) {
			    try {
                    if (rc.getRoundNum() < EARLY_GAME) {
                        if (SURROUNDING_TREES_NEUTRAL.length > 0 && !spawnedLumberjack) {
                            spawnUnit(RobotType.LUMBERJACK, SPAWN_DIR);
                            spawnedLumberjack = true;
                        }
                    } else {
                        if (SURROUNDING_TREES_NEUTRAL.length > 0) {
                            spawnUnit(RobotType.LUMBERJACK, SPAWN_DIR);
                        }
                    }
                } catch (Exception e){
			        e.printStackTrace();
                }
			}

			//Plant trees
			if (!emergency){
				try {
					for (Direction dir : current_available_slots) {
						if (rc.canPlantTree(dir)) rc.plantTree(dir);
					}
					//Try forcing 5 trees around gardener
					if (SURROUNDING_TREES_OWN.length < 5 && SURROUNDING_TREES_OWN.length > 0) {
						Direction cDir = MY_LOCATION.directionTo(SURROUNDING_TREES_OWN[0].location);
						for (int i = 0; i < 5; i++) {
							cDir = new Direction(absRad(cDir.radians + DEG_60));
							if (rc.canPlantTree(cDir)){
								System.out.println("Forcing planting of tree at: "+Float.toString(cDir.radians));
								float angle_diff = absRad(cDir.radians - SPAWN_DIR.radians);
								if (angle_diff > DEG_180) angle_diff = DEG_360-angle_diff;
								if (angle_diff >= DEG_60) rc.plantTree(cDir);
							}
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			//Water
			TreeInfo lowestHealthTree = null;
			float lowestHealth = 1000000f;
			for (TreeInfo tree : SURROUNDING_TREES_OWN){
				if (tree.getHealth() < lowestHealth){
					lowestHealth = tree.getHealth();
					lowestHealthTree = tree;
				}
			}
			if (lowestHealthTree != null){
				if (rc.canWater(lowestHealthTree.getID())){
					try {
						rc.water(lowestHealthTree.getID());
					} catch (GameActionException e) {
						e.printStackTrace();
					}
				}
			}

            if (rc.getRoundNum() >= soldierTimer && SPAWN_DIR != null){
                try {
					if (rand.nextFloat() < SCOUT_SPAWN_CHANCE){
						if (spawnUnit(RobotType.SCOUT, SPAWN_DIR)){
							soldierTimer = rc.getRoundNum() + SCOUT_DELAY;
						}
					}
                    else if (spawnUnit(RobotType.SOLDIER, SPAWN_DIR)) {
                        soldierTimer = rc.getRoundNum() + SOLDIER_DELAY;
                    }
                } catch (Exception e){
                    e.printStackTrace();
                }
            }

		}

    }
	
	static void runLumberjack(){
		//Set important variables
		if (!isSet){
			target = null;
			targetInfo = null;
			visitedArr = new ArrayList<MapLocation>(); //Need to include in every robot
			patience = 0;
			isInterruptable = false;
			isSet = true;
			inCombat = false;
		}
		//Look for nearby enemies
		if (!inCombat && SURROUNDING_ROBOTS_ENEMY.length > 0){
			//Go into combat
			inCombat = true;
			spotEnemy(SURROUNDING_ROBOTS_ENEMY[0]);
		}
		if (inCombat && SURROUNDING_ROBOTS_ENEMY.length == 0){
			//Snap out of combat
			inCombat = false;
		}
		if (inCombat){
			//Find nearest enemy
			float nearestdist = 1000000f;
			RobotInfo nearestEnemy = null;
			for (RobotInfo enemy : SURROUNDING_ROBOTS_ENEMY){
				float dist = enemy.getLocation().distanceSquaredTo(MY_LOCATION);
				if (dist < nearestdist){
					nearestdist = dist;
					nearestEnemy = enemy;
				}
			}
			//Move to nearest enemy's position
			target = nearestEnemy.getLocation();
			moveToTarget(MY_LOCATION);
			//Attack nearest enemy if possible
			//No micro here => He should micro to a position with the most enemies and least allies
			if (rc.canStrike()){
				try {
					rc.strike();
				} catch (GameActionException e) {
					e.printStackTrace();
				}
			}
		}
		else{
			//Chop down trees if not in combat
			if (target == null || isInterruptable){ //Find a new target
				TreeInfo nearestTree = null;
				if (SURROUNDING_TREES_NEUTRAL.length>0) nearestTree = SURROUNDING_TREES_NEUTRAL[0];
				if (nearestTree != null){ //No more trees
					target = nearestTree.getLocation();
					targetInfo = nearestTree;
					isInterruptable = false;
				}
				else if (target == null){ //Don't replace with scouting if target is not null
					//No more trees => TODO: Some scouting
					target = newScoutingLocation(20); //Go to a random scouting location with 20 attempts
					isInterruptable = true;
				}
			}
			if (target != null){
				//Check if has approached target
				boolean done = false;
				if (rc.canChop(target)){
					try{
						//System.out.println("Chop chop");
						rc.chop(target);
						done = true;
					}
					catch (GameActionException e){
						e.printStackTrace();
					}
				}
				
				//If target is gone
				if (MY_LOCATION.isWithinDistance(target, 1)){
					target = null;
				}
				/*if (target != null){
					System.out.println("Target: " + target);
				}
				else{
					System.out.println("Null Target");
				}*/
				//Move toward target if not in range
				if (!done && target != null){
					moveToTarget(MY_LOCATION);
				}
			}
		}
	}

	static void runSoldier() {
		//Set important variables
		if (!isSet){
			target = null;
			visitedArr = new ArrayList<MapLocation>(); //Need to include in every robot
			patience = 0;
			inCombat = false;

			isSet = true;
		}
        boolean lone_archon = false;

		//Look for nearby enemies
		BulletInfo [] nearbyBullets = rc.senseNearbyBullets(BULLET_SENSE_RANGE);
		if (!inCombat && SURROUNDING_ROBOTS_ENEMY.length > 0){
            if (rc.getRoundNum() < EARLY_GAME && SURROUNDING_ROBOTS_ENEMY.length == 1){
                if (SURROUNDING_ROBOTS_ENEMY[0].type == RobotType.ARCHON){
                    System.out.println("Only archon detected...");
                    inCombat = false;
                    lone_archon = true;
                }
                else inCombat = true;
            }
            else inCombat = true;
            //Go into combat
            target = null;

            if(!lone_archon) {
                //Call all noncombat soldiers to enemy position
                RobotInfo nearestEnemy = SURROUNDING_ROBOTS_ENEMY[0];
                spotEnemy(nearestEnemy);
            }
		}
		if (!inCombat){
			//If someone has spotted an enemy
			try{
                int turnSpotted = rc.readBroadcast(SPOTTED_TURN);
                int locationSpotted = rc.readBroadcast(SPOTTED_LOCATION);
                int enemySpotted = rc.readBroadcast(SPOTTED_ENEMY_TYPE);
				//Alarm all other soldiers
				if (turnSpotted >= rc.getRoundNum() - SOLDIER_ALARM_RELEVANCE){
					target = intToMapLocation(locationSpotted);
				}
			}
			catch (GameActionException e){
				e.printStackTrace();
			}
		}
		if (inCombat && SURROUNDING_ROBOTS_ENEMY.length == 0){
			//Snap out of combat
			target = null;
			inCombat = false;
		}
		if (inCombat){
            //Find nearest enemy
            float nearestdist = 1000000f;
            RobotInfo nearestEnemy = null;
            nearestEnemy = SURROUNDING_ROBOTS_ENEMY[0];
            nearestdist = MY_LOCATION.distanceSquaredTo(SURROUNDING_ROBOTS_ENEMY[0].location);
            //Don't engage early game ARCHON
            if (rc.getRoundNum() < EARLY_GAME){
                if (SURROUNDING_ROBOTS_ENEMY[0].type == RobotType.ARCHON){
                    System.out.println("Ignoring early game archon");
                    if (SURROUNDING_ROBOTS_ENEMY.length == 1){
                        target = null;
                        inCombat = false;
                        lone_archon = true;
                    }
                    else {
                        nearestEnemy = SURROUNDING_ROBOTS_ENEMY[1];
                        nearestdist = MY_LOCATION.distanceSquaredTo(SURROUNDING_ROBOTS_ENEMY[1].location);
                    }
                }
            }

            System.out.println("Right before moving: "+Integer.toString(Clock.getBytecodeNum()));

            if (!lone_archon) {
                //rc.setIndicatorDot(MY_LOCATION, 255, 0, 255);

                //Attack before moving
                //System.out.println("Nearest Enemy Dist: "+nearestdist);
                if (nearestdist <= SOLDIER_MICRO_DIST || nearbyBullets.length > 0) {
                    //System.out.println("I'm making my COMBAT MOVE");
                    soldierCombatMove(nearestEnemy, SURROUNDING_ROBOTS_ENEMY, nearbyBullets);
                    //rc.setIndicatorDot(rc.getLocation().add(new Direction(0f), 0.1f), 255, 100, 100);
                    System.out.println("After micro: " + Integer.toString(Clock.getBytecodeNum()));
                } else {
                    //Move to nearest enemy's position
                    target = nearestEnemy.getLocation();
                    if (am_stuck) {
                        search_dir = !search_dir;
                        am_stuck = false;
                        staying_conut = 0;
                    }
                    moveToTarget_lightweight(MY_LOCATION, search_dir);
                    //rc.setIndicatorDot(rc.getLocation().add(new Direction(0f), 0.1f), 100, 255, 100);
                    System.out.println("After moving: " + Integer.toString(Clock.getBytecodeNum()));
                }

                //MY_LOCATION = rc.getLocation();
            }

		}
		else{
			if (target == null){
				target = newScoutingLocation(20);
			}
			if (target != null){
				/*try {
					//rc.setIndicatorDot(target, 0, 0, 255);
				} catch (GameActionException e) {
					e.printStackTrace();
				}*/
                if (am_stuck){
                    search_dir = !search_dir;
                    am_stuck = false;
                    staying_conut = 0;
                }
                moveToTarget_experimental(MY_LOCATION, search_dir);
				if (target != null){
					if (MY_LOCATION.distanceSquaredTo(target) <=TARGET_RELEASE_DIST){ //If at target, go scout somewhere else
						target = newScoutingLocation(20);
					}
				}
			}
		}
		if (target != null){
			//System.out.println("Target: "+target);
			//System.out.println("myPos: "+myLoc);
			//System.out.println("inCombat: "+inCombat);
		}
	}

	static void runScout() {
		//Broadcast whether scout is present
		/*try {
			rc.broadcast(21, rc.getRoundNum());
		} catch (GameActionException e) {
			e.printStackTrace();
		}*/
		//Set important variables
		if (!isSet){
			target = ENEMY_ARCHON_LOCATION;
			visitedArr = new ArrayList<MapLocation>(); //Need to include in every robot
			patience = 0;
			inCombat = false;
			isSet = true;
		}
		boolean lone_archon = (SURROUNDING_ROBOTS_ENEMY.length > 0 && SURROUNDING_ROBOTS_ENEMY[0].type == RobotType.ARCHON && SURROUNDING_ROBOTS_ENEMY.length == 1);

		//Look for nearby enemies
		BulletInfo [] nearbyBullets = rc.senseNearbyBullets(BULLET_SENSE_RANGE);
		if (!inCombat && SURROUNDING_ROBOTS_ENEMY.length > 0){
			//Don't engage early game ARCHON
			inCombat = true;
            if (rc.getRoundNum() < MID_GAME && lone_archon){
				System.out.println("Only archon detected...");
				inCombat = false;
			}
			//Go into combat
			target = null;
			
			if (SURROUNDING_ROBOTS_ENEMY.length > 1 && SURROUNDING_ROBOTS_ENEMY[0].type == RobotType.ARCHON){ 
				//If more than one enemy and enemy not an archon
                RobotInfo nearestEnemy = SURROUNDING_ROBOTS_ENEMY[1];
                spotEnemy(nearestEnemy);
            }
			else if (!lone_archon) {
				//Call all noncombat soldiers to enemy position
				RobotInfo nearestEnemy = SURROUNDING_ROBOTS_ENEMY[0];
				spotEnemy(nearestEnemy);
			}
		}
		if (!inCombat){
			//If someone has spotted an enemy. Scouts not to be used to fight after early game
			try{
			    if (rc.getRoundNum() <= MID_GAME) {
                    int turnSpotted = rc.readBroadcast(SPOTTED_TURN);
                    int locationSpotted = rc.readBroadcast(SPOTTED_LOCATION);
                    int enemySpotted = rc.readBroadcast(SPOTTED_ENEMY_TYPE);
                    if (turnSpotted >= rc.getRoundNum() - SOLDIER_ALARM_RELEVANCE) {
                        target = intToMapLocation(locationSpotted);
                    }
                }
			}
			catch (GameActionException e){
				e.printStackTrace();
			}
		}
		if (inCombat && SURROUNDING_ROBOTS_ENEMY.length == 0){
			//Snap out of combat
			target = null;
			inCombat = false;
		}
		if (inCombat){
			//Find nearest enemy
			RobotInfo nearestEnemy = null;
			nearestEnemy = SURROUNDING_ROBOTS_ENEMY[0];
			//Don't engage early game ARCHON
            if (rc.getRoundNum() < MID_GAME){
				if (lone_archon){
					//Snap out of combat
					target = null;
					inCombat = false;
				}
				else if (SURROUNDING_ROBOTS_ENEMY.length > 1 && SURROUNDING_ROBOTS_ENEMY[0].type == RobotType.ARCHON){ 
					//If more than one enemy and enemy not an archon
	                nearestEnemy = SURROUNDING_ROBOTS_ENEMY[1];
	            }
			}
            
            float nearestdist = MY_LOCATION.distanceSquaredTo(nearestEnemy.location);

			if (!lone_archon) {

				//Attack before moving
				//System.out.println("Nearest Enemy Dist: "+nearestdist);
				if (nearestdist <= SCOUT_MICRO_DIST || nearbyBullets.length > 0) {
					//System.out.println("I'm making my COMBAT MOVE");
					scoutCombatMove(nearestEnemy, SURROUNDING_ROBOTS_ENEMY, nearbyBullets);
				} else {
					//Move to nearest enemy's position
					target = nearestEnemy.getLocation();
					moveToTarget(MY_LOCATION);
				}

				MY_LOCATION = rc.getLocation();
			}
			else{
				//Snap out of combat
				inCombat = false;
				if (target == null){
					//A somewhat ballsy change
					MY_LOCATION = rc.getLocation();
					float furthestDist = -1000000f;
					MapLocation furthestArchonLoc = null;
					MapLocation [] initArchonLocs = rc.getInitialArchonLocations(rc.getTeam().opponent());
					for (int i = 0; i < initArchonLocs.length; i++){
						float dist = MY_LOCATION.distanceTo(initArchonLocs[i]);
						if (dist > furthestDist){
							furthestDist = dist;
							furthestArchonLoc = initArchonLocs[i];
						}
					}
					target = furthestArchonLoc;
					//target = newScoutingLocation(20);
				}
				if (target != null){
					moveToTarget(MY_LOCATION);
					if (target != null){
						if (MY_LOCATION.distanceSquaredTo(target) <=TARGET_RELEASE_DIST){ //If at target, go scout somewhere else
							target = newScoutingLocation(20);
						}
					}
				}
			}

		}
		else{
			if (target == null){
				//A somewhat ballsy change
				MY_LOCATION = rc.getLocation();
				float furthestDist = -1000000f;
				MapLocation furthestArchonLoc = null;
				MapLocation [] initArchonLocs = rc.getInitialArchonLocations(rc.getTeam().opponent());
				for (int i = 0; i < initArchonLocs.length; i++){
					float dist = MY_LOCATION.distanceTo(initArchonLocs[i]);
					if (dist > furthestDist){
						furthestDist = dist;
						furthestArchonLoc = initArchonLocs[i];
					}
				}
				target = furthestArchonLoc;
			}
			if (target != null){
				/*try {
					//rc.setIndicatorDot(target, 0, 0, 255);
				} catch (GameActionException e) {
					e.printStackTrace();
				}*/
				moveToTarget(MY_LOCATION);
				if (target != null){
					if (MY_LOCATION.distanceSquaredTo(target) <=TARGET_RELEASE_DIST){ //If at target, go scout somewhere else
						target = newScoutingLocation(20);
					}
				}
			}
		}
		if (target != null){
			//System.out.println("Target: "+target);
			//System.out.println("myPos: "+myLoc);
			//System.out.println("inCombat: "+inCombat);
		}
	}

	static void runTank() {
		//Set important variables
		if (!isSet){
			target = null;
			visitedArr = new ArrayList<MapLocation>(); //Need to include in every robot
			patience = 0;
			inCombat = false;

			isSet = true;
		}
		//Look for nearby enemies
		if (!inCombat && SURROUNDING_ROBOTS_ENEMY.length > 0){
			//Go into combat
			inCombat = true;
			target = null;
			//Call all noncombat soldiers to enemy position
			float nearestdist = 1000000f;
			RobotInfo nearestEnemy = null;
			for (RobotInfo enemy : SURROUNDING_ROBOTS_ENEMY){
				float dist = enemy.getLocation().distanceSquaredTo(MY_LOCATION);
				if (dist < nearestdist){
					nearestdist = dist;
					nearestEnemy = enemy;
				}
			}
			spotEnemy(nearestEnemy);
		}
		if (!inCombat){
			//If someone has spotted an enemy
			try{
                int turnSpotted = rc.readBroadcast(SPOTTED_TURN);
                int locationSpotted = rc.readBroadcast(SPOTTED_LOCATION);
                int enemySpotted = rc.readBroadcast(SPOTTED_ENEMY_TYPE);
				//Alarm all other soldiers
				if (turnSpotted >= rc.getRoundNum() - SOLDIER_ALARM_RELEVANCE){
					target = intToMapLocation(locationSpotted);
				}
			}
			catch (GameActionException e){
				e.printStackTrace();
			}
		}
		if (inCombat && SURROUNDING_ROBOTS_ENEMY.length == 0){
			//Snap out of combat
			target = null;
			inCombat = false;
		}
		if (inCombat){
			//Find nearest enemy
			float nearestdist = 1000000f;
			RobotInfo nearestEnemy = null;
			for (RobotInfo enemy : SURROUNDING_ROBOTS_ENEMY){
				float dist = enemy.getLocation().distanceSquaredTo(MY_LOCATION);
				if (dist < nearestdist){
					nearestdist = dist;
					nearestEnemy = enemy;
				}
			}
			//Move to nearest enemy's position
			target = nearestEnemy.getLocation();
			moveToTarget(MY_LOCATION);
			//Attack nearest enemy if possible
			//No micro here => Just shooting bullets at the nearest enemy
			if (MY_LOCATION.isWithinDistance(target, SOLDIER_SHOOT_RANGE)){
				try {
					if (rc.canFirePentadShot()){
						//todo Check for friendly fire
						/*RobotInfo[] friendly = rc.senseNearbyRobots(-1, rc.getTeam());
						boolean friendlyFire = false;
						for(RobotInfo f:friendly){
							if(intersectLineCircle(myLoc,f.location,myLoc.directionTo(f.location))){
								friendlyFire = true;
							}
						}
						if(!friendlyFire) rc.firePentadShot(myLoc.directionTo(target));*/
						rc.firePentadShot(MY_LOCATION.directionTo(target));
					}
				} catch (GameActionException e) {
					e.printStackTrace();
				}
			}

		}
		else{
			//Look for trees to crush! hehehe :D
			TreeInfo closest_tree = null;
			if (SURROUNDING_TREES_ENEMY.length > 0) closest_tree = SURROUNDING_TREES_ENEMY[0];
			else if (SURROUNDING_TREES_NEUTRAL.length > 0) closest_tree = SURROUNDING_TREES_NEUTRAL[0];

			if (closest_tree != null){
				target = closest_tree.location;
			}

			if (target == null){
				target = newScoutingLocation(20);
			}
			else{
				/*try {
					//rc.setIndicatorDot(target, 0, 0, 255);
				} catch (GameActionException e) {
					e.printStackTrace();
				}*/
				moveToTarget(MY_LOCATION);
				if (target != null){
					if (MY_LOCATION.distanceSquaredTo(target) <= TARGET_RELEASE_DIST){ //If at target, go scout somewhere else
						target = newScoutingLocation(20);
					}
				}
			}
		}
		if (target != null){
			//System.out.println("Target: "+target);
			//System.out.println("myPos: "+myLoc);
			//System.out.println("inCombat: "+inCombat);
		}
	}

	//==========================================================================
	//<--------------------------- METRIC FUNCTIONS --------------------------->
	//==========================================================================
	//Metric to test if gardeners are spaced out enough :)
	static boolean isSpreadOut(MapLocation myLoc, float radius){
		RobotInfo[] farmers = rc.senseNearbyRobots(radius, rc.getTeam());
		float distance_to_archon = 999f;

		//Avoid gardeners/archons
		for (RobotInfo f:farmers) if (f.type == RobotType.GARDENER || f.type == RobotType.ARCHON) return false;

		//Check distance to archon
		RobotInfo[] archons = rc.senseNearbyRobots(-1, rc.getTeam());
		for (RobotInfo a:archons){
			if (a.type == RobotType.ARCHON){
				float curDist = myLoc.distanceTo(a.location);
				System.out.println("ARCHON NEARBY!!! DISTANCE: "+Float.toString(curDist));
				if (curDist<distance_to_archon) distance_to_archon = curDist;
			}
		}

		if (distance_to_archon < FARMER_ARCHON_DISTANCE) return false;

		//Avoids all friendly robots
		//if (farmers.length > 0) return false;

		return true;
	}

	//Building slots checking
	static boolean slot_free_metric(MapLocation origin, Direction dir) throws GameActionException {
		//return (rc.isCircleOccupied(origin.add(dir,2.01f),1f));
		return rc.canPlantTree(dir);
	}

	//Better checking build slots functions :D
	static ArrayList<Direction> getBuildSlots(MapLocation origin, int MIN_SLOTS) throws GameActionException {
		ArrayList<Direction> available_slots = new ArrayList<Direction>();
		//Scan in a hexagonal pattern first
		for (int i=0;i<20;i++){
			if (available_slots.size() > MIN_SLOTS) return available_slots;
			else available_slots.clear();
			Direction curDir = new Direction(absRad(DEG_1*3*i));
			if (!slot_free_metric(origin,curDir)) continue;
			else {
				available_slots.add(curDir);
				curDir = curDir.rotateRightRads(DEG_60);
				for (int j=0;j<48;j++){
					if ((48-j)*DEG_1*5 < (MIN_SLOTS - available_slots.size() - 1)*DEG_60) break;
					if (slot_free_metric(origin,curDir)){
						available_slots.add(curDir);
						curDir = curDir.rotateRightRads(DEG_60);
						j+=12;
					}
					else curDir = curDir.rotateRightRads(DEG_1*5);
				}
			}
		}

		return available_slots;
	}

	//Get Density of objects (area)
	static float getDensity(MapLocation center, float radius) {
	    try {
            if (!rc.onTheMap(center)) return 1f;
        } catch (Exception e){
	        e.printStackTrace();
        }
		RobotInfo[] robots = rc.senseNearbyRobots(center, radius, null);
		TreeInfo[] trees = rc.senseNearbyTrees(center, radius, null);
		float robots_area = 0;
		float tree_area = 0;
		//for (RobotInfo r:robots) robots_area += r.getRadius()*r.getRadius()*DEG_180;
		for (TreeInfo t:trees){
			//calculates partial area inside scan radius
			float r = t.radius;
			float R = radius;
			float d = center.distanceTo(t.location);
			if(R < r){
				r = radius;
				R = t.radius;
			}
            float part1 = r*r*(float)Math.acos((d*d + r*r - R*R)/(2*d*r));
            float part2 = R*R*(float)Math.acos((d*d + R*R - r*r)/(2*d*R));
            float part3 = 0.5f*(float)Math.sqrt((-d+r+R)*(d+r-R)*(d-r+R)*(d+r+R));
            float intersectionArea = part1 + part2 - part3;

			tree_area += intersectionArea;
		}
		return (robots_area+tree_area) / (radius*radius*DEG_180);
	}

	//Get Density of objects
	static float getDensity_linear(MapLocation center,float radius){
		RobotInfo[] robots = rc.senseNearbyRobots(center, radius, null);
		TreeInfo[] trees = rc.senseNearbyTrees(center, radius, null);
		return (float)(robots.length+trees.length)/(radius);
	}

	//Get Density of robots from the team
	static float getDensity_robots(MapLocation center,float radius){
        try {
            if (!rc.onTheMap(center)) return 1f;
        } catch (Exception e){
            e.printStackTrace();
        }
		RobotInfo[] robots = rc.senseNearbyRobots(center, radius, null);
		float robots_area = 0;
		//for (RobotInfo r:robots) robots_area += r.getRadius()*r.getRadius()*DEG_180;
		for (RobotInfo bot:robots){
			//calculates partial area inside scan radius
			float r = bot.getRadius();
			float R = radius;
			float d = center.distanceTo(bot.location);
			if(R < r){
				r = radius;
				R = bot.getRadius();
			}
            float part1 = r*r*(float)Math.acos((d*d + r*r - R*R)/(2*d*r));
            float part2 = R*R*(float)Math.acos((d*d + R*R - r*r)/(2*d*R));
            float part3 = 0.5f*(float)Math.sqrt((-d+r+R)*(d+r-R)*(d-r+R)*(d+r+R));
            float intersectionArea = part1 + part2 - part3;

			robots_area += intersectionArea;
		}
		TreeInfo[] trees = rc.senseNearbyTrees(center, radius, null);
		float tree_area = 0;
		for (TreeInfo t:trees){
			//calculates partial area inside scan radius
			float r = t.radius;
			float R = radius;
			float d = center.distanceTo(t.location);
			if(R < r){
				r = radius;
				R = t.radius;
			}
            float part1 = r*r*(float)Math.acos((d*d + r*r - R*R)/(2*d*r));
            float part2 = R*R*(float)Math.acos((d*d + R*R - r*r)/(2*d*R));
            float part3 = 0.5f*(float)Math.sqrt((-d+r+R)*(d+r-R)*(d-r+R)*(d+r+R));
			float intersectionArea = part1 + part2 - part3;

			tree_area += intersectionArea;
		}
		return (robots_area+tree_area) / (radius*radius*DEG_180);
	}

    //Get Density of surrounding neutral trees
    static float getDensity_trees(MapLocation center,float radius){
        TreeInfo[] trees = rc.senseNearbyTrees(center, radius, Team.NEUTRAL);
        if (trees.length == 0) return 0f;
        float tree_area = 0f;
        for (TreeInfo t:trees){
            //calculates partial area inside scan radius
            float r = t.radius;
            float R = radius;
            float d = center.distanceTo(t.location);
            if(R < r){
                r = radius;
                R = t.radius;
            }
            float part1 = r*r*(float)Math.acos((d*d + r*r - R*R)/(2*d*r));
            float part2 = R*R*(float)Math.acos((d*d + R*R - r*r)/(2*d*R));
            float part3 = 0.5f*(float)Math.sqrt((-d+r+R)*(d+r-R)*(d-r+R)*(d+r+R));
            float intersectionArea = part1 + part2 - part3;

            tree_area += intersectionArea;
        }
        return (tree_area) / (radius*radius*DEG_180);
    }

	//==========================================================================
	//<--------------------------- MICRO FUNCTIONS --------------------------->
	//==========================================================================
    //<-------------- THE ALMIGHTY PATHFINDING THINGUMMYWHAT ----------------->
    static void moveToTarget_experimental(MapLocation myLoc, boolean rightBot){
	    //Chosen direction of travel
        Direction initDir = rc.getLocation().directionTo(target); //Direction chosen
        int sensorDir = 0;

        //Get closest sensor angle to travel direction
        for (int i=0;i<SENSOR_GRANULARITY;i++){
            if (initDir.radians > SENSOR_ANGLES[i]) sensorDir = i;
        }
        if (initDir.radians - SENSOR_ANGLES[sensorDir] > (DEG_360/SENSOR_GRANULARITY/2)) sensorDir = absIdx(sensorDir++, SENSOR_GRANULARITY);

        //Get reading from 'sensor'
        /*float[] sensor_feedback = new float[SENSOR_GRANULARITY];
        MapLocation scanning_loc = null;
        Direction cDir = new Direction(SENSOR_ANGLES[0]);
        float current_density = 0;
        for (int i=0;i<SENSOR_GRANULARITY;i++){
            scanning_loc = MY_LOCATION.add(cDir, MOVEMENT_SCANNING_DISTANCE);
            current_density = getDensity_robots(scanning_loc, MOVEMENT_SCANNING_RADIUS);
            sensor_feedback[i] = current_density;
            System.out.println("Sensor Reading at: "+Float.toString(DEG_360/SENSOR_GRANULARITY*i)+"\n"+Float.toString(sensor_feedback[i]));
            if (rc.canMove(cDir)) //rc.setIndicatorDot(scanning_loc, 0, 0, 255);
            else //rc.setIndicatorDot(scanning_loc, 255, 0, 0);
            cDir = new Direction(SENSOR_ANGLES[i]);
        }*/
        //Show movement direction
        /*Direction cDir = new Direction(SENSOR_ANGLES[0]);
        MapLocation scanning_loc = null;
        for (int i=0;i<SENSOR_GRANULARITY;i++){
            scanning_loc = MY_LOCATION.add(cDir, MOVEMENT_SCANNING_DISTANCE);
            if (rc.canMove(cDir)) rc.setIndicatorDot(scanning_loc, 0, 0, 255);
            else rc.setIndicatorDot(scanning_loc, 255, 0, 0);
            cDir = new Direction(SENSOR_ANGLES[i]);
        }
        rc.setIndicatorDot(MY_LOCATION.add(initDir, MOVEMENT_SCANNING_DISTANCE), 0, 255, 0);*/

        //Bug pathing algorithm!! :O
        //Right turning bot...

        //Try trivial case
        try {
            if (rc.canMove(initDir)){
                rc.move(initDir);
                return;
            }
            else{
                if (rc.canSenseAllOfCircle(myLoc.add(initDir), rc.getType().bodyRadius) && !rc.onTheMap(myLoc.add(initDir, rc.getType().bodyRadius))) updateBorders(myLoc);
                //Try jiggling about the direction of movement
                if (!am_stuck) {
                    for (int i = 1; i < BASHING_GRANULARITY; i++) {
                        Direction tryDir = new Direction(absRad(initDir.radians + DEG_1 * i));
                        if (rc.canMove(tryDir)) {
                            rc.move(tryDir);
                            //rc.setIndicatorDot(MY_LOCATION.add(tryDir, MOVEMENT_SCANNING_DISTANCE), 0, 255, 255);
                            return;
                        }
                        tryDir = new Direction(absRad(initDir.radians - DEG_1 * i));
                        if (rc.canMove(tryDir)) {
                            rc.move(tryDir);
                            //rc.setIndicatorDot(MY_LOCATION.add(tryDir, MOVEMENT_SCANNING_DISTANCE), 0, 255, 255);
                            return;
                        }
                    }
                }

                //Uncomplicated bug pathing code
                if (rightBot) {
                    for (int i = 1; i < SENSOR_GRANULARITY; i++) {
                        Direction tryDir = new Direction(SENSOR_ANGLES[absIdx(sensorDir + i, SENSOR_GRANULARITY)]);
                        if (rc.canMove(tryDir)) {
                            rc.move(tryDir);
                            //rc.setIndicatorDot(MY_LOCATION.add(tryDir, MOVEMENT_SCANNING_DISTANCE), 0, 255, 255);
                            return;
                        }
                    }
                }
                else{
                    for (int i = 1; i < SENSOR_GRANULARITY; i++) {
                        Direction tryDir = new Direction(SENSOR_ANGLES[absIdx(sensorDir - i, SENSOR_GRANULARITY)]);
                        if (rc.canMove(tryDir)) {
                            rc.move(tryDir);
                            //rc.setIndicatorDot(MY_LOCATION.add(tryDir, MOVEMENT_SCANNING_DISTANCE), 0, 255, 255);
                            return;
                        }
                    }
                }
                /*for (int i=1;i<SENSOR_GRANULARITY/2;i++){
                    Direction tryDir = new Direction(SENSOR_ANGLES[absIdx(sensorDir-i, SENSOR_GRANULARITY)]);
                    if (rc.canMove(tryDir)){
                        rc.move(tryDir);
                        //rc.setIndicatorDot(MY_LOCATION.add(tryDir, MOVEMENT_SCANNING_DISTANCE), 0, 255, 255);
                        return;
                    }
                }*/
            }
//            else {
//                //Complicated nonsense with density readings and whatnot
//                boolean reverse_direction = false;
//                float lowest_density_reading = 999999f;
//                float lowest_density_dir = SENSOR_ANGLES[sensorDir];
//                boolean found_max = false;
//                float max_density_reading = sensor_feedback[sensorDir];
//                float max_density_dir = SENSOR_ANGLES[sensorDir];
//                for (int i=0;i<SENSOR_GRANULARITY;i++){
//                    int curIdx = absIdx(i+sensorDir, SENSOR_GRANULARITY);
//                    if (found_max) {
//                        if (sensor_feedback[absIdx(curIdx + 1, SENSOR_GRANULARITY)] > sensor_feedback[curIdx] + SENSOR_SMOOTHENING_THRESHOLD || sensor_feedback[curIdx] < SENSOR_SMOOTHENING_THRESHOLD) {
//                            //Scanned past local minimum, stop || scanned into open_space, stop, follow edge instead
//                            if (rc.canMove(new Direction(SENSOR_ANGLES[curIdx]))) {
//                                rc.move(new Direction(SENSOR_ANGLES[curIdx]));
//                                //Show movement direction
//                                //rc.setIndicatorDot(MY_LOCATION.add(new Direction(SENSOR_ANGLES[curIdx]), MOVEMENT_SCANNING_DISTANCE), 0, 255, 255);
//                                return;
//                            } else {
//                                //try going the other direction
//                                reverse_direction = true;
//                                break;
//                            }
//                        }
//                    }
//                    else{
//                        if (sensor_feedback[absIdx(curIdx + 1, SENSOR_GRANULARITY)] < max_density_reading - SENSOR_SMOOTHENING_THRESHOLD) found_max = true;
//                    }
//                    if (sensor_feedback[curIdx] < lowest_density_reading){
//                        lowest_density_dir = SENSOR_ANGLES[curIdx];
//                        lowest_density_reading = sensor_feedback[curIdx];
//                    }
//                    if (sensor_feedback[curIdx] > max_density_reading){
//                        max_density_dir = SENSOR_ANGLES[curIdx];
//                        max_density_reading = sensor_feedback[curIdx];
//                    }
//                }
//                if (reverse_direction){
//                    lowest_density_reading = 9999999f;
//                    lowest_density_dir = SENSOR_ANGLES[sensorDir];
//                    found_max = false;
//                    max_density_reading = sensor_feedback[sensorDir];
//                    max_density_dir = SENSOR_ANGLES[sensorDir];
//                    for (int i=0;i<SENSOR_GRANULARITY;i++){
//                        int curIdx = absIdx(sensorDir-i, SENSOR_GRANULARITY);
//                        if (found_max) {
//                            if (sensor_feedback[absIdx(curIdx - 1, SENSOR_GRANULARITY)] > sensor_feedback[curIdx] + SENSOR_SMOOTHENING_THRESHOLD || sensor_feedback[curIdx] < SENSOR_SMOOTHENING_THRESHOLD) {
//                                //Scanned past local minimum, stop || scanned into open_space, stop, follow edge instead
//                                if (rc.canMove(new Direction(SENSOR_ANGLES[curIdx]))) {
//                                    rc.move(new Direction(SENSOR_ANGLES[curIdx]));
//                                    //Show movement direction
//                                    //rc.setIndicatorDot(MY_LOCATION.add(new Direction(SENSOR_ANGLES[curIdx]), MOVEMENT_SCANNING_DISTANCE), 0, 255, 255);
//                                    return;
//                                }
//                            }
//                        }
//                        else{
//                            if (sensor_feedback[absIdx(curIdx - 1, SENSOR_GRANULARITY)] < max_density_reading - SENSOR_SMOOTHENING_THRESHOLD) found_max = true;
//                        }
//                        if (sensor_feedback[curIdx] < lowest_density_reading){
//                            lowest_density_dir = SENSOR_ANGLES[curIdx];
//                            lowest_density_reading = sensor_feedback[curIdx];
//                        }
//                        if (sensor_feedback[curIdx] > max_density_reading){
//                            max_density_dir = SENSOR_ANGLES[curIdx];
//                            max_density_reading = sensor_feedback[curIdx];
//                        }
//                    }
//                }
//                //I've scanned all around, still nowhere to move...
//
//                //Remember to update borders!!
//                if (rc.canSenseAllOfCircle(myLoc.add(new Direction(lowest_density_dir)), rc.getType().bodyRadius) && !rc.onTheMap(myLoc.add(new Direction(lowest_density_dir), rc.getType().bodyRadius))) updateBorders(myLoc);
//
//            }

        } catch (Exception e){
            e.printStackTrace();
        }

	    return;
    }

    static void moveToTarget_lightweight(MapLocation myLoc, boolean rightBot){
        //Chosen direction of travel
        Direction initDir = rc.getLocation().directionTo(target); //Direction chosen
        int sensorDir = 0;

        //Get closest sensor angle to travel direction
        for (int i=0;i<SOLDIER_CHASING_GRANULARITY;i++){
            if (initDir.radians > LIGHTWEIGHT_SENSOR_ANGLES[i]) sensorDir = i;
        }
        if (initDir.radians - LIGHTWEIGHT_SENSOR_ANGLES[sensorDir] > (DEG_360/SOLDIER_CHASING_GRANULARITY/2)) sensorDir = absIdx(sensorDir++, SOLDIER_CHASING_GRANULARITY);

        //Get reading from 'sensor'
        /*float[] sensor_feedback = new float[SOLDIER_CHASING_GRANULARITY];
        MapLocation scanning_loc = null;
        Direction cDir = new Direction(LIGHTWEIGHT_SENSOR_ANGLES[0]);
        float current_density = 0;
        for (int i=0;i<SOLDIER_CHASING_GRANULARITY;i++){
            scanning_loc = MY_LOCATION.add(cDir, MOVEMENT_SCANNING_DISTANCE);
            current_density = getDensity_robots(scanning_loc, MOVEMENT_SCANNING_RADIUS);
            sensor_feedback[i] = current_density;
            System.out.println("Sensor Reading at: "+Float.toString(DEG_360/SOLDIER_CHASING_GRANULARITY*i)+"\n"+Float.toString(sensor_feedback[i]));
            if (rc.canMove(cDir)) //rc.setIndicatorDot(scanning_loc, 0, 0, 255);
            else //rc.setIndicatorDot(scanning_loc, 255, 0, 0);
            cDir = new Direction(LIGHTWEIGHT_SENSOR_ANGLES[i]);
        }*/
        //Show movement direction
       /* Direction cDir = new Direction(LIGHTWEIGHT_SENSOR_ANGLES[0]);
        MapLocation scanning_loc = null;
        for (int i=0;i<SOLDIER_CHASING_GRANULARITY;i++){
            scanning_loc = MY_LOCATION.add(cDir, MOVEMENT_SCANNING_DISTANCE);
            //if (rc.canMove(cDir)) rc.setIndicatorDot(scanning_loc, 0, 0, 255);
            //else rc.setIndicatorDot(scanning_loc, 255, 0, 0);
            cDir = new Direction(LIGHTWEIGHT_SENSOR_ANGLES[i]);
        }
        //rc.setIndicatorDot(MY_LOCATION.add(initDir, MOVEMENT_SCANNING_DISTANCE), 0, 255, 0);*/

        //Bug pathing algorithm!! :O
        //Right turning bot...

        //Try trivial case
        try {
            if (rc.canMove(initDir)){
                rc.move(initDir);
                return;
            }
            else{
                if (rc.canSenseAllOfCircle(myLoc.add(initDir), rc.getType().bodyRadius) && !rc.onTheMap(myLoc.add(initDir, rc.getType().bodyRadius))) updateBorders(myLoc);
                //Try jiggling about the direction of movement
                for (int i=1;i<MOVEMENT_GRANULARITY;i++){
                    Direction tryDir = new Direction(absRad(initDir.radians + DEG_1*i));
                    if (rc.canMove(tryDir)) {
                        rc.move(tryDir);
                        //rc.setIndicatorDot(MY_LOCATION.add(tryDir, MOVEMENT_SCANNING_DISTANCE), 0, 255, 255);
                        return;
                    }
                    tryDir = new Direction(absRad(initDir.radians - DEG_1*i));
                    if (rc.canMove(tryDir)) {
                        rc.move(tryDir);
                        //rc.setIndicatorDot(MY_LOCATION.add(tryDir, MOVEMENT_SCANNING_DISTANCE), 0, 255, 255);
                        return;
                    }
                }

                //Uncomplicated bug pathing code
                if (rightBot) {
                    for (int i = 1; i < SOLDIER_CHASING_GRANULARITY; i++) {
                        Direction tryDir = new Direction(LIGHTWEIGHT_SENSOR_ANGLES[absIdx(sensorDir + i, SOLDIER_CHASING_GRANULARITY)]);
                        if (rc.canMove(tryDir)) {
                            rc.move(tryDir);
                            //rc.setIndicatorDot(MY_LOCATION.add(tryDir, MOVEMENT_SCANNING_DISTANCE), 0, 255, 255);
                            return;
                        }
                    }
                }
                else{
                    for (int i = 1; i < SOLDIER_CHASING_GRANULARITY; i++) {
                        Direction tryDir = new Direction(LIGHTWEIGHT_SENSOR_ANGLES[absIdx(sensorDir - i, SOLDIER_CHASING_GRANULARITY)]);
                        if (rc.canMove(tryDir)) {
                            rc.move(tryDir);
                            //rc.setIndicatorDot(MY_LOCATION.add(tryDir, MOVEMENT_SCANNING_DISTANCE), 0, 255, 255);
                            return;
                        }
                    }
                }
                /*for (int i=1;i<SOLDIER_CHASING_GRANULARITY/2;i++){
                    Direction tryDir = new Direction(LIGHTWEIGHT_SENSOR_ANGLES[absIdx(sensorDir-i, SOLDIER_CHASING_GRANULARITY)]);
                    if (rc.canMove(tryDir)){
                        rc.move(tryDir);
                        //rc.setIndicatorDot(MY_LOCATION.add(tryDir, MOVEMENT_SCANNING_DISTANCE), 0, 255, 255);
                        return;
                    }
                }*/
            }
//            else {
//                //Complicated nonsense with density readings and whatnot
//                boolean reverse_direction = false;
//                float lowest_density_reading = 999999f;
//                float lowest_density_dir = SENSOR_ANGLES[sensorDir];
//                boolean found_max = false;
//                float max_density_reading = sensor_feedback[sensorDir];
//                float max_density_dir = SENSOR_ANGLES[sensorDir];
//                for (int i=0;i<SENSOR_GRANULARITY;i++){
//                    int curIdx = absIdx(i+sensorDir, SENSOR_GRANULARITY);
//                    if (found_max) {
//                        if (sensor_feedback[absIdx(curIdx + 1, SENSOR_GRANULARITY)] > sensor_feedback[curIdx] + SENSOR_SMOOTHENING_THRESHOLD || sensor_feedback[curIdx] < SENSOR_SMOOTHENING_THRESHOLD) {
//                            //Scanned past local minimum, stop || scanned into open_space, stop, follow edge instead
//                            if (rc.canMove(new Direction(SENSOR_ANGLES[curIdx]))) {
//                                rc.move(new Direction(SENSOR_ANGLES[curIdx]));
//                                //Show movement direction
//                                //rc.setIndicatorDot(MY_LOCATION.add(new Direction(SENSOR_ANGLES[curIdx]), MOVEMENT_SCANNING_DISTANCE), 0, 255, 255);
//                                return;
//                            } else {
//                                //try going the other direction
//                                reverse_direction = true;
//                                break;
//                            }
//                        }
//                    }
//                    else{
//                        if (sensor_feedback[absIdx(curIdx + 1, SENSOR_GRANULARITY)] < max_density_reading - SENSOR_SMOOTHENING_THRESHOLD) found_max = true;
//                    }
//                    if (sensor_feedback[curIdx] < lowest_density_reading){
//                        lowest_density_dir = SENSOR_ANGLES[curIdx];
//                        lowest_density_reading = sensor_feedback[curIdx];
//                    }
//                    if (sensor_feedback[curIdx] > max_density_reading){
//                        max_density_dir = SENSOR_ANGLES[curIdx];
//                        max_density_reading = sensor_feedback[curIdx];
//                    }
//                }
//                if (reverse_direction){
//                    lowest_density_reading = 9999999f;
//                    lowest_density_dir = SENSOR_ANGLES[sensorDir];
//                    found_max = false;
//                    max_density_reading = sensor_feedback[sensorDir];
//                    max_density_dir = SENSOR_ANGLES[sensorDir];
//                    for (int i=0;i<SENSOR_GRANULARITY;i++){
//                        int curIdx = absIdx(sensorDir-i, SENSOR_GRANULARITY);
//                        if (found_max) {
//                            if (sensor_feedback[absIdx(curIdx - 1, SENSOR_GRANULARITY)] > sensor_feedback[curIdx] + SENSOR_SMOOTHENING_THRESHOLD || sensor_feedback[curIdx] < SENSOR_SMOOTHENING_THRESHOLD) {
//                                //Scanned past local minimum, stop || scanned into open_space, stop, follow edge instead
//                                if (rc.canMove(new Direction(SENSOR_ANGLES[curIdx]))) {
//                                    rc.move(new Direction(SENSOR_ANGLES[curIdx]));
//                                    //Show movement direction
//                                    //rc.setIndicatorDot(MY_LOCATION.add(new Direction(SENSOR_ANGLES[curIdx]), MOVEMENT_SCANNING_DISTANCE), 0, 255, 255);
//                                    return;
//                                }
//                            }
//                        }
//                        else{
//                            if (sensor_feedback[absIdx(curIdx - 1, SENSOR_GRANULARITY)] < max_density_reading - SENSOR_SMOOTHENING_THRESHOLD) found_max = true;
//                        }
//                        if (sensor_feedback[curIdx] < lowest_density_reading){
//                            lowest_density_dir = SENSOR_ANGLES[curIdx];
//                            lowest_density_reading = sensor_feedback[curIdx];
//                        }
//                        if (sensor_feedback[curIdx] > max_density_reading){
//                            max_density_dir = SENSOR_ANGLES[curIdx];
//                            max_density_reading = sensor_feedback[curIdx];
//                        }
//                    }
//                }
//                //I've scanned all around, still nowhere to move...
//
//                //Remember to update borders!!
//                if (rc.canSenseAllOfCircle(myLoc.add(new Direction(lowest_density_dir)), rc.getType().bodyRadius) && !rc.onTheMap(myLoc.add(new Direction(lowest_density_dir), rc.getType().bodyRadius))) updateBorders(myLoc);
//
//            }

        } catch (Exception e){
            e.printStackTrace();
        }

        return;
    }

    //Movement Function
    //Note: MapLocation target must have been set, visitedArr and patience should be initialised
    static void moveToTarget(MapLocation myLoc){
        Direction initDir = rc.getLocation().directionTo(target); //Direction chosen
        boolean hasMoved = false;
        for (int i = 0; i < MOVEMENT_GRANULARITY; i++){
            if (i == 0){ //Try the trivial case
                Direction approachDir = myLoc.directionTo(target);
                MapLocation closestApproach = target.subtract(approachDir, 1 + rc.getType().bodyRadius);
                if (rc.canMove(closestApproach)){
                    try {
                        //System.out.println("Closest Approach !!!");
                        rc.move(closestApproach);
                    } catch (GameActionException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
            Direction chosenDir; //Direction chosen by loop
            if (i % 2 == 0){
                chosenDir = new Direction((float)(initDir.radians + (i / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY));
            }
            else{
                chosenDir = new Direction((float)(initDir.radians - ((i + 1) / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY));
            }

            if (rc.canMove(chosenDir)){
                boolean isValid = true;
                if (isValid){
                    try{
                        rc.move(chosenDir);
                        hasMoved = true;
                        break;
                    }
                    catch (GameActionException e){
                        e.printStackTrace();
                    }
                }
            }
            else if (i == 0){ //Detect if target is out of bounds
                try {
                    if (rc.canSenseAllOfCircle(myLoc.add(chosenDir), rc.getType().bodyRadius) && !rc.onTheMap(myLoc.add(chosenDir), rc.getType().bodyRadius)){
                        //System.out.println("I think I'm out of bounds");
                        //System.out.println(myLoc);
                        //Target is out of bounds
                        target = null;
                        //Update known bounds
                        updateBorders(myLoc);
                    }
                } catch (GameActionException e) {
                    e.printStackTrace();
                }
            }
        }
        if (visitedArr.size() >= VISITED_ARR_SIZE){
            //Alt way of checking if stuck
            float expecteddist = rc.getType().strideRadius * VISITED_ARR_SIZE;
            float actualdist = visitedArr.get(0).distanceTo(myLoc);
            if (actualdist / expecteddist < STUCK_THRESHOLD && rc.getType() != RobotType.SOLDIER && rc.getType() != RobotType.TANK && rc.getType() != RobotType.SCOUT){ //Help I'm stuck [Gardener no longer moves]
                //Not sure if this helps to avoid getting stuck
                target = null; //Reset target
            }
            visitedArr.remove(0);
        }
        visitedArr.add(rc.getLocation());
    }

    //<---------------------------- GARDENER MICRO --------------------------->
    //New gardener movement algo
    static void gardenerMove(int num_attempts){ //Bounces the gardener around
        while (num_attempts > 0){
            if (!rc.canMove(moveDir)){
                moveDir = randomDirection();
            }
            else{
                try{
                    rc.move(moveDir);
                    break;
                }
                catch (GameActionException e){
                    e.printStackTrace();
                }
            }
            num_attempts --;
        }
        /*MapLocation scanning_loc = null;
        MapLocation least_dense_loc = null;
        Direction cDir = new Direction(0f);
        Direction least_dense_dir = null;
        float current_density = 0;
        float least_density = 999999f;
        for (int i=0;i<num_attempts;i++){
            scanning_loc = MY_LOCATION.add(cDir, MOVEMENT_SCANNING_DISTANCE);
            current_density = getDensity_robots(scanning_loc, MOVEMENT_SCANNING_RADIUS);
            if (current_density < least_density && rc.canMove(cDir)){
                least_density = current_density;
                least_dense_dir = cDir;
                least_dense_loc = scanning_loc;
            }
            if (rc.canMove(cDir)) //rc.setIndicatorDot(scanning_loc, 0, 0, 255);
            else //rc.setIndicatorDot(scanning_loc, 255, 0, 0);
            cDir = cDir.rotateRightRads(DEG_360/num_attempts);
        }
        if (least_dense_loc != null) //rc.setIndicatorDot(least_dense_loc, 0, 255, 0);
        if (least_dense_dir != null){
            try{
                rc.move(least_dense_dir,Math.min(1,2*getDensity(MY_LOCATION.add(least_dense_dir.opposite(), MOVEMENT_SCANNING_DISTANCE), MOVEMENT_SCANNING_RADIUS)));
            } catch(Exception e){
                e.printStackTrace();
            }
        }*/
        return;
    }

    //<----------------------------- ARCHON MICRO ---------------------------->
	static float getArchonScore(MapLocation loc, ArrayList<RobotInfo> nearbyLumberjacks, ArrayList<RobotInfo> nearbyCombatRobots, BulletInfo[] nearbyBullets){
		float ans = 0;
		//Avoid lumberjacks
		for (RobotInfo lumberjack : nearbyLumberjacks){ //Heavy cost of 1000
			if (lumberjack.getLocation().distanceTo(loc) <= LUMBERJACK_AVOIDANCE_RANGE_ARCHON){
				//System.out.println(lumberjack.getLocation().distanceTo(loc));
				ans -= RobotType.LUMBERJACK.attackPower * distanceScaling(lumberjack.getLocation().distanceTo(loc)) * 1000f;
			}
		}

		//Avoid bullets [Heavy penalty]
		for (BulletInfo bullet : nearbyBullets){
			if (willCollideWithMe(loc, bullet)){
				ans -= bullet.getDamage() * 1000f;
			}
		}

		//Be as far as possible from the soldiers
		for (RobotInfo robot : nearbyCombatRobots){
			ans -= distanceScaling(loc.distanceTo(robot.getLocation()));
		}

		return ans;
	}

	static void archonCombatMove(RobotInfo nearestEnemy, RobotInfo[] nearbyEnemies, BulletInfo[] nearbyBullets) {
		MapLocation myLoc = rc.getLocation();
		ArrayList<RobotInfo> nearbyLumberjacks = new ArrayList<RobotInfo>();
		ArrayList<RobotInfo> nearbyCombatUnits = new ArrayList<RobotInfo>();
		boolean hasMoved = false; //Have I moved already?

		//Process nearby enemies
		for (RobotInfo enemy : nearbyEnemies){
			if (enemy.getType() == RobotType.LUMBERJACK){
				nearbyLumberjacks.add(enemy);
			}
			else if (enemy.getType() != RobotType.ARCHON && enemy.getType() != RobotType.GARDENER){
				nearbyCombatUnits.add(enemy);
			}
		}

		//System.out.println(nearbyLumberjacks);

		//Move in the 8 directions


		float maxScore = -1000000f;
		MapLocation prevtarget = target;
		if (nearestEnemy != null){
			target = nearestEnemy.getLocation();
		}
		else{
			//Is this a good idea?
			target = rc.getInitialArchonLocations(rc.getTeam().opponent())[0];
		}

		Direction initDir = rc.getLocation().directionTo(target); //Direction chosen
		Direction finalDir = null; //Final move direction
		for (int i = 0; i < MOVEMENT_GRANULARITY; i++){
			Direction chosenDir; //Direction chosen by loop
			if (i % 2 == 0){
				chosenDir = new Direction((float)(initDir.radians + (i / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY));
			}
			else{
				chosenDir = new Direction((float)(initDir.radians - ((i + 1) / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY));
			}

			if (rc.canMove(chosenDir)){
				float score = getArchonScore(myLoc.add(chosenDir, rc.getType().strideRadius), nearbyLumberjacks, nearbyCombatUnits, nearbyBullets);
				//System.out.println("Direction: ( "+chosenDir.getDeltaX(1)+" , "+chosenDir.getDeltaY(1)+" )");
				//System.out.println("Score: "+score);
				if (score > maxScore){
					maxScore = score;
					finalDir = chosenDir;
				}
			}
		}
		if (finalDir != null){
			try{
				if (rc.canMove(finalDir)){
					rc.move(finalDir);
				}
			}
			catch (GameActionException e){
				e.printStackTrace();
			}
		}
		return;

	}

	//<----------------------------- SCOUT MICRO ----------------------------->
	static float getScoutScore(MapLocation loc, RobotInfo nearestGardener, ArrayList<RobotInfo> nearbyLumberjacks, ArrayList<RobotInfo> nearbyCombatRobots, BulletInfo[] nearbyBullets){
		float ans = 0;
		//Avoid lumberjacks
		for (RobotInfo lumberjack : nearbyLumberjacks){ //Heavy cost of 1000
			if (lumberjack.getLocation().distanceTo(loc) <= LUMBERJACK_AVOIDANCE_RANGE){
				//System.out.println(lumberjack.getLocation().distanceTo(loc));
				ans -= RobotType.LUMBERJACK.attackPower * 1000f;
			}
		}

		//Avoid bullets [Heavy penalty]
		for (BulletInfo bullet : nearbyBullets){
			if (willCollideWithMe(loc, bullet)){
				ans -= bullet.getDamage() * 1000f;
			}
		}

		//Be as close as possible to the gardener
		if (nearestGardener != null){
			ans += distanceScaling(loc.distanceTo(nearestGardener.getLocation()));
		}

		//Be as far as possible from the soldiers
		for (RobotInfo robot : nearbyCombatRobots){
			ans -= distanceScaling(loc.distanceTo(robot.getLocation())) / OFFENSE_FACTOR;
		}

		return ans;
	}

	static void scoutCombatMove(RobotInfo nearestEnemy, RobotInfo[] nearbyEnemies, BulletInfo[] nearbyBullets){
		MapLocation myLoc = rc.getLocation();
		TreeInfo[] nearbyTrees = rc.senseNearbyTrees(-1, rc.getTeam().opponent());
		RobotInfo nearestGardener = null;
		float nearestGardenerDist = 1000000f;
		RobotInfo nearestLumberjack = null;
		float nearestLumberjackDist = 1000000f;
		ArrayList<RobotInfo> nearbyLumberjacks = new ArrayList<RobotInfo>();
		ArrayList<RobotInfo> nearbyCombatUnits = new ArrayList<RobotInfo>();
		boolean hasMoved = false; //Have I moved already?

		//Process nearby enemies
		for (RobotInfo enemy : nearbyEnemies){
			if (enemy.getType() == RobotType.LUMBERJACK){
				float dist = myLoc.distanceSquaredTo(enemy.getLocation());
				if (dist < nearestLumberjackDist){
					nearestLumberjackDist = dist;
					nearestLumberjack = enemy;
				}
				nearbyLumberjacks.add(enemy);
			}
			else if (enemy.getType() == RobotType.GARDENER){
				float dist = myLoc.distanceSquaredTo(enemy.getLocation());
				if (dist < nearestGardenerDist){
					nearestGardenerDist = dist;
					nearestGardener = enemy;
				}
			}
			else if (enemy.getType() != RobotType.ARCHON){
				nearbyCombatUnits.add(enemy);
			}
		}

		//System.out.println(nearbyLumberjacks);

		//Avoid friendly fire
		RobotInfo [] nearbyAllies = rc.senseNearbyRobots(SCOUT_COMBAT_SENSE_RADIUS, rc.getTeam());

		//Conduct attack
		if (rc.canFireSingleShot()){
			MapLocation attackLoc = null;
			if (nearestGardener != null){
				attackLoc = nearestGardener.getLocation();
			}
			else if (nearestLumberjack != null){
				attackLoc = nearestLumberjack.getLocation();
			}
			else{
				attackLoc = nearestEnemy.getLocation();
			}
			if (attackLoc != null){
				Direction shotDir = myLoc.directionTo(attackLoc);
				MapLocation shotLoc = myLoc.add(shotDir, rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET);
				boolean safeToShoot = true;
				for (RobotInfo ally : nearbyAllies){
					if (willCollideWithRobot(shotDir, shotLoc, ally.getLocation(), ally.getType().bodyRadius)){
						safeToShoot = false;
						break;
					}
				}
				//Shoot if at point blank range
				if (!safeToShoot){
					if (nearestEnemy.getLocation().distanceTo(MY_LOCATION) < rc.getType().bodyRadius + nearestEnemy.getRadius() + POINT_BLANK_RANGE){
						safeToShoot = true;
					}
				}
				if (safeToShoot){
					try {
						rc.fireSingleShot(myLoc.directionTo(attackLoc));
					} catch (GameActionException e) {
						e.printStackTrace();
					}
				}
			}
		}

		nearbyBullets = rc.senseNearbyBullets(BULLET_SENSE_RANGE);


		float nearestDistToGardener = 10000000f;
		MapLocation bestMoveLoc = null;
		//See if there is any tree available
		for (TreeInfo tree : nearbyTrees){
			boolean canMove = true;
			for (RobotInfo lumberjack : nearbyLumberjacks){
				//System.out.println("Dist: "+tree.getLocation().distanceTo(lumberjack.getLocation()));
				if (tree.getLocation().distanceTo(lumberjack.getLocation()) <= LUMBERJACK_AVOIDANCE_RANGE_SCOUT){
					canMove = false;
					break;
				}
			}
			//Direct towards nearest gardener
			if (canMove && nearestGardener != null){
				MapLocation moveLoc = tree.getLocation().subtract(tree.getLocation().directionTo(nearestGardener.getLocation()), 0.01f);
				if (rc.canMove(moveLoc)){
					float distToGardener = moveLoc.distanceTo(nearestGardener.getLocation());
					if (distToGardener < nearestDistToGardener){
						nearestDistToGardener = distToGardener;
						bestMoveLoc = moveLoc;
					}
				}
			}
			else if (canMove && nearestGardener == null){
				//Position away from any bullet
				if (nearbyBullets.length > 0){
					MapLocation newLoc = tree.getLocation();
					BulletInfo relBullet = null;
					for (BulletInfo bullet : nearbyBullets){
						if (willCollideWithMe(newLoc, bullet)){
							relBullet = bullet;
							break;
						}
					}
					MapLocation moveLoc;
					if (relBullet == null){
						moveLoc = tree.getLocation().subtract(tree.getLocation().directionTo(nearbyBullets[0].getLocation()), 0.01f);
					}
					else{
						moveLoc = tree.getLocation().add(relBullet.getDir(), 0.01f);
					}
					if (rc.canMove(moveLoc)  && moveLoc.distanceTo(myLoc) <= rc.getType().strideRadius){
						try{
							rc.move(moveLoc);
							hasMoved = true;
							return;
						}
						catch (GameActionException e){
							e.printStackTrace();
						}
					}
				}
				else if (nearestEnemy != null){ //Position away from some enemy
					MapLocation moveLoc = tree.getLocation().subtract(tree.getLocation().directionTo(nearestEnemy.getLocation()), 0.01f);
					if (rc.canMove(moveLoc) && moveLoc.distanceTo(myLoc) <= rc.getType().strideRadius){
						try{
							rc.move(moveLoc);
							hasMoved = true;
							return;
						}
						catch (GameActionException e){
							e.printStackTrace();
						}
					}
				}
			}
		}

		if (!hasMoved && bestMoveLoc != null){
			try{
				if (rc.canMove(bestMoveLoc)){
					if (bestMoveLoc.distanceTo(myLoc) <= rc.getType().strideRadius){
						rc.move(bestMoveLoc);
						hasMoved = true;
						return;
					}
					else{
						nearestGardener = new RobotInfo(nearestGardener.ID, nearestGardener.team, nearestGardener.type,
								bestMoveLoc, nearestGardener.health, nearestGardener.attackCount, nearestGardener.moveCount);
					}
				}
			}
			catch (GameActionException e){
				e.printStackTrace();
			}
		}
		//If no trees available then try the 8 directions

		if (hasMoved){
			return;
		}


		float maxScore = -1000000f;
		MapLocation prevtarget = target;
		if (nearestGardener != null){
			target = nearestGardener.getLocation();
		}
		else{
			//Is this a good idea?
			target = rc.getInitialArchonLocations(rc.getTeam().opponent())[0];
		}
		Direction initDir = rc.getLocation().directionTo(target); //Direction chosen
		Direction finalDir = null; //Final move direction
		for (int i = 0; i < MOVEMENT_GRANULARITY_MICRO; i++){
			Direction chosenDir; //Direction chosen by loop
			if (i % 2 == 0){
				chosenDir = new Direction((float)(initDir.radians + (i / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY_MICRO));
			}
			else{
				chosenDir = new Direction((float)(initDir.radians - ((i + 1) / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY_MICRO));
			}

			if (rc.canMove(chosenDir)){
				////rc.setIndicatorDot(myLoc.add(chosenDir, rc.getType().strideRadius), 0, 255, 0);
				float score = getScoutScore(myLoc.add(chosenDir, rc.getType().strideRadius), nearestGardener, nearbyLumberjacks, nearbyCombatUnits, nearbyBullets);
				//System.out.println("Direction: ( "+chosenDir.getDeltaX(1)+" , "+chosenDir.getDeltaY(1)+" )");
				//System.out.println("Score: "+score);
				if (score > maxScore){
					maxScore = score;
					finalDir = chosenDir;
				}
			}
			else{
				////rc.setIndicatorDot(myLoc.add(chosenDir, rc.getType().strideRadius), 255, 0, 0);
			}
		}

		//Sometimes the best move is not to move
		float score = getScoutScore(myLoc, nearestGardener, nearbyLumberjacks, nearbyCombatUnits, nearbyBullets);
		if (score > maxScore){
			maxScore = score;
			finalDir = null;
		}

		if (finalDir != null){
			try{
				if (rc.canMove(finalDir)){
					rc.move(finalDir);
				}
			}
			catch (GameActionException e){
				e.printStackTrace();
			}
		}


		if (nearestGardener == null){
			return;
		}
		return;
	}

	//<---------------------------- SOLDIER MICRO ---------------------------->
	static float getSoldierScore(MapLocation loc, ArrayList<RobotInfo> nearbyLumberjacks, ArrayList<RobotInfo> nearbyCombatRobots, BulletInfo[] nearbyBullets){
		float ans = 0;
		//Avoid lumberjacks
		for (RobotInfo lumberjack : nearbyLumberjacks){ //Heavy cost of 1000
			if (lumberjack.getLocation().distanceTo(loc) <= LUMBERJACK_AVOIDANCE_RANGE){
				//System.out.println(lumberjack.getLocation().distanceTo(loc));
				ans -= rc.getType().attackPower * 1000f;
			}
			else break;
		}

		//Avoid bullets [Heavy penalty]
		for (BulletInfo bullet : nearbyBullets){
			if (willCollideWithMe(loc, bullet)){
				ans -= bullet.getDamage() * 1000f;
			}
		}

		//Be as far as possible from the soldiers
		for (RobotInfo robot : nearbyCombatRobots){
			ans -= distanceScaling(loc.distanceTo(robot.getLocation()));
		}

		return ans;
	}

	static void soldierCombatMove(RobotInfo nearestEnemy, RobotInfo[] nearbyEnemies, BulletInfo[] nearbyBullets){
		MapLocation myLoc = rc.getLocation();
		ArrayList<RobotInfo> nearbyLumberjacks = new ArrayList<RobotInfo>();
		ArrayList<RobotInfo> nearbyCombatUnits = new ArrayList<RobotInfo>();
		boolean hasMoved = false; //Have I moved already?

		//Process nearby enemies
		for (RobotInfo enemy : nearbyEnemies){
			if (enemy.getType() == RobotType.LUMBERJACK){
				nearbyLumberjacks.add(enemy);
			}
			else if (enemy.getType() != RobotType.ARCHON){
				nearbyCombatUnits.add(enemy);
			}
		}

		//System.out.println(nearbyLumberjacks);

		//Avoid friendly fire
		RobotInfo [] nearbyAllies = rc.senseNearbyRobots(SOLDIER_COMBAT_SENSE_RADIUS, rc.getTeam());
		//Should I use -1?

		boolean hasFired = false;

		//Fire switches
		boolean should_fire_pentad = true;
		boolean should_fire_triad = true;
		/*if (nearestEnemy != null){
			if (myLoc.distanceTo(nearestEnemy.location) > PENTAD_EFFECTIVE_DISTANCE){
				should_fire_pentad = false;
				if (nearestEnemy.type == RobotType.LUMBERJACK || nearestEnemy.type == RobotType.ARCHON || nearestEnemy.type == RobotType.SCOUT ){
					should_fire_triad = false;
				}
			}
		}*/

		//Conduct attack
		//Avoid friendly fire
		if (rc.canFirePentadShot() && should_fire_pentad){
			MapLocation attackLoc = null;
			attackLoc = nearestEnemy.getLocation();
			if (attackLoc != null){
				boolean safeToShoot = true;
				for (int i = 0; i < 5; i++){
					Direction shotDir = null;
					if (i <= 1){
						shotDir = myLoc.directionTo(attackLoc).rotateLeftDegrees(GameConstants.PENTAD_SPREAD_DEGREES * (2 - i));
					}
					else if (i == 2){
						shotDir = myLoc.directionTo(attackLoc);
					}
					else{
						shotDir = myLoc.directionTo(attackLoc).rotateRightDegrees(GameConstants.PENTAD_SPREAD_DEGREES * (i - 2));
					}
					MapLocation shotLoc = myLoc.add(shotDir, rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET);

					for (RobotInfo ally : nearbyAllies){
						if (willCollideWithRobot(shotDir, shotLoc, ally.getLocation(), ally.getType().bodyRadius)){
							safeToShoot = false;
							break;
						}
					}
					if (!safeToShoot){
						break;
					}
				}
				if (safeToShoot){
					try {
						rc.firePentadShot(myLoc.directionTo(attackLoc));
						hasFired = true;
					} catch (GameActionException e) {
						e.printStackTrace();
					}
				}
			}
		}
		if (!hasFired && rc.canFireTriadShot() && should_fire_triad){
			MapLocation attackLoc = null;
			attackLoc = nearestEnemy.getLocation();
			if (attackLoc != null){
				boolean safeToShoot = true;
				for (int i = 0; i < 3; i++){
					Direction shotDir = null;
					if (i == 0){
						shotDir = myLoc.directionTo(attackLoc).rotateLeftDegrees(GameConstants.TRIAD_SPREAD_DEGREES);
					}
					else if (i == 1){
						shotDir = myLoc.directionTo(attackLoc);
					}
					else{
						shotDir = myLoc.directionTo(attackLoc).rotateRightDegrees(GameConstants.TRIAD_SPREAD_DEGREES);
					}
					MapLocation shotLoc = myLoc.add(shotDir, rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET);

					for (RobotInfo ally : nearbyAllies){
						if (willCollideWithRobot(shotDir, shotLoc, ally.getLocation(), ally.getType().bodyRadius)){
							safeToShoot = false;
							break;
						}
					}
					if (!safeToShoot){
						break;
					}
				}
				if (safeToShoot){
					try {
						rc.fireTriadShot(myLoc.directionTo(attackLoc));
					} catch (GameActionException e) {
						e.printStackTrace();
					}
				}
			}
		}
		if (!hasFired && rc.canFireSingleShot()){
			MapLocation attackLoc = null;
			attackLoc = nearestEnemy.getLocation();
			if (attackLoc != null){
				boolean safeToShoot = true;
				Direction shotDir = myLoc.directionTo(attackLoc);
				MapLocation shotLoc = myLoc.add(shotDir, rc.getType().bodyRadius + GameConstants.BULLET_SPAWN_OFFSET);

				for (RobotInfo ally : nearbyAllies){
					if (willCollideWithRobot(shotDir, shotLoc, ally.getLocation(), ally.getType().bodyRadius)){
						safeToShoot = false;
						break;
					}
				}
				if (safeToShoot){
					try {
						rc.fireSingleShot(myLoc.directionTo(attackLoc));
					} catch (GameActionException e) {
						e.printStackTrace();
					}
				}
			}
		}

		nearbyBullets = rc.senseNearbyBullets(BULLET_SENSE_RANGE);
		System.out.println("How many frigging bullets are there?? "+Integer.toString(nearbyBullets.length));

		//Move in the 8 directions


		float maxScore = -1000000f;
		MapLocation prevtarget = target;
		if (nearestEnemy != null){
			target = nearestEnemy.getLocation();
		}
		else{
			//Is this a good idea?
			target = rc.getInitialArchonLocations(rc.getTeam().opponent())[0];
		}

		Direction initDir = rc.getLocation().directionTo(target); //Direction chosen
		Direction finalDir = null; //Final move direction
		for (int i = 0; i < MOVEMENT_GRANULARITY_MICRO; i++){
			Direction chosenDir; //Direction chosen by loop
			if (i % 2 == 0){
				chosenDir = new Direction((float)(initDir.radians + (i / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY_MICRO));
			}
			else{
				chosenDir = new Direction((float)(initDir.radians - ((i + 1) / 2) * 2 * Math.PI / MOVEMENT_GRANULARITY_MICRO));
			}

			if (rc.canMove(chosenDir)){
				////rc.setIndicatorDot(myLoc.add(chosenDir, rc.getType().strideRadius), 0, 255, 0);
				float score = getSoldierScore(myLoc.add(chosenDir, rc.getType().strideRadius), nearbyLumberjacks, nearbyCombatUnits, nearbyBullets);
				//System.out.println("Direction: ( "+chosenDir.getDeltaX(1)+" , "+chosenDir.getDeltaY(1)+" )");
				//System.out.println("Score: "+score);
				if (score > maxScore){
					maxScore = score;
					finalDir = chosenDir;
				}
			}
			else{
				////rc.setIndicatorDot(myLoc.add(chosenDir, rc.getType().strideRadius), 255, 0, 0);
			}
		}

		//Sometimes the best move is not to move
		float score = getSoldierScore(myLoc, nearbyLumberjacks, nearbyCombatUnits, nearbyBullets);
		if (score > maxScore){
			maxScore = score;
			finalDir = null;
		}

		if (finalDir != null){
			target = MY_LOCATION.add(finalDir, 5f);
            moveToTarget_lightweight(MY_LOCATION, true);
		}
		return;
	}

	//==========================================================================
	//<--------------------------- HELPER FUNCTIONS --------------------------->
	//==========================================================================
	static boolean willCollideWithRobot(Direction bulletDir, MapLocation bulletLoc, MapLocation robotLoc, float robotBodyRadius) {

		// Get relevant bullet information
		Direction propagationDirection = bulletDir;
		MapLocation bulletLocation = bulletLoc;

		// Calculate bullet relations to this robot
		Direction directionToRobot = bulletLocation.directionTo(robotLoc);
		float distToRobot = bulletLocation.distanceTo(robotLoc);
		float theta = propagationDirection.radiansBetween(directionToRobot);

		// If theta > 90 degrees, then the bullet is traveling away from us and we can break early
		if (Math.abs(theta) > Math.PI/2) {
			return false;
		}

		// distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
		// This is the distance of a line that goes from myLocation and intersects perpendicularly with propagationDirection.
		// This corresponds to the smallest radius circle centered at our location that would intersect with the
		// line that is the path of the bullet.
		float perpendicularDist = (float)Math.abs(distToRobot * SIN_VALUES[absIdx((int)(absRad(theta)/DEG_1),360)]); // soh cah toa :)
        //float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)

		return (perpendicularDist <= robotBodyRadius);
	}

	static boolean willCollideWithMe(MapLocation newLoc, BulletInfo bullet) {
        System.out.println("Collision function: "+Integer.toString(Clock.getBytecodeNum()));
	    MapLocation myLocation = newLoc;

		// Get relevant bullet information
		Direction propagationDirection = bullet.dir;
		MapLocation bulletLocation = bullet.location;

		// Calculate bullet relations to this robot
		Direction directionToRobot = bulletLocation.directionTo(myLocation);
		float distToRobot = bulletLocation.distanceTo(myLocation);
		float theta = propagationDirection.radiansBetween(directionToRobot);

		// If theta > 90 degrees, then the bullet is traveling away from us and we can break early
		if (Math.abs(theta) > Math.PI/2) {
			return false;
		}

		// distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
		// This is the distance of a line that goes from myLocation and intersects perpendicularly with propagationDirection.
		// This corresponds to the smallest radius circle centered at our location that would intersect with the
		// line that is the path of the bullet.
        System.out.println(absRad(theta));
        System.out.println((int)(absRad(theta)/DEG_1));

		float perpendicularDist = (float)Math.abs(distToRobot * SIN_VALUES[absIdx((int)(absRad(theta)/DEG_1),360)]); // soh cah toa :)
        //float perpendicularDist = (float)Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)

		return (perpendicularDist <= rc.getType().bodyRadius);
	}

	static float distanceScaling(float dist){
		if (dist <= 1.0f){
			return 10f; //Very good
		}
		else{
			return 10f / dist; //Not so good
		}
	}

	static boolean spawnUnit(RobotType type, Direction dir){
    	if (rc.canBuildRobot(type, dir)){
    		try {
				rc.buildRobot(type, dir);
				return true;
			} catch (GameActionException e) {
				e.printStackTrace();
			}
    	}
    	return false;
    }

    static Direction randomDirection(){
    	//System.out.println(new Direction((float)Math.random() - 0.5f, (float)Math.random() - 0.5f));
    	return new Direction((float)Math.random() - 0.5f, (float)Math.random() - 0.5f);
    }

    static void updateBorders(MapLocation myLoc){
    	//0 => left border, 1 => top border, 2 => right border, 3 => bottom border
    	Direction[] dir = {new Direction(-1, 0), new Direction(0, -1), new Direction(1,0), new Direction(0,1)};
    	for (int i = 0; i < 4; i++){
	    	try{
	    		if (rc.getType() == RobotType.SOLDIER || rc.getType() == RobotType.TANK){
	    			MapLocation newLoc = myLoc.add(dir[i], 7);
	    		}
	    		MapLocation newLoc = myLoc.add(dir[i], 4);
	    		/*if (rc.canSenseLocation(newLoc)){
	    			System.out.println("Hai");
	    		}
	    		if (rc.onTheMap(newLoc)){
	    			System.out.println("HaiHai");
	    		}*/
	    		//System.out.println(newLoc);
	    		if (rc.canSenseLocation(newLoc) && !rc.onTheMap(newLoc)){
	    			if (i % 2 == 0){
	    				System.out.println("Updated border " + i + " : " + myLoc.x);
	    				rc.broadcast(i, (int)myLoc.x);
	    			}
	    			else{
	    				System.out.println("Updated border " + i + " : " + myLoc.y);
	    				rc.broadcast(i, (int)myLoc.y);
	    			}
	    			
	    		}
	    	}
	    	catch (GameActionException e){
	    		e.printStackTrace();
	    	}
    	}
    }

    //Generate a new maplocation which is not yet scouted
    static MapLocation newScoutingLocation(int max_attempts){
    	try {
			int leftborder = rc.readBroadcast(0);
			int topborder = rc.readBroadcast(1);
			int rightborder = rc.readBroadcast(2);
			int bottomborder = rc.readBroadcast(3);
			//System.out.println("Borders: "+ leftborder + " " + topborder + " " + rightborder + " " + bottomborder);
			while (max_attempts > 0){
				float x = (float)(leftborder + (rightborder - leftborder) * Math.random());
				float y = (float)(topborder + (bottomborder - topborder) * Math.random());
				MapLocation loc = new MapLocation(x,y);
				if (!rc.canSenseLocation(loc)){
					//System.out.println("x: " + x + " y: " + y);
					return loc;
				}
				max_attempts --;
			}
			return null;
		} catch (GameActionException e) {
			e.printStackTrace();
			return null;
		}
    }

    static int mapLocationToInt(MapLocation loc){
    	return ((int)(loc.x) * 1000 + (int)(loc.y));
    }
    
    static MapLocation intToMapLocation(int loc){
    	return new MapLocation((float)(loc / 1000), (float)(loc % 1000));
    }
    
    static void spotEnemy(RobotInfo enemy){
    	if (enemy.getType() == RobotType.ARCHON){
    		return;
    	}
    	try{
			rc.broadcast(SPOTTED_TURN, rc.getRoundNum());
    		rc.broadcast(SPOTTED_LOCATION, mapLocationToInt(enemy.location));
    		rc.broadcast(SPOTTED_ENEMY_TYPE, robotType(enemy.type));
    	}
    	catch (GameActionException e){
    		e.printStackTrace();
    	}
    }

    static int robotType(RobotType type){
        switch (type){
            case ARCHON:
                return 1;
            case GARDENER:
                return 2;
            case LUMBERJACK:
                return 3;
            case SOLDIER:
                return 4;
            case SCOUT:
                return 5;
            case TANK:
                return 6;
            default:
                return 0;
        }
    }

    static RobotType robotType(int i){
        switch (i){
            case 1:
                return RobotType.ARCHON;
            case 2:
                return RobotType.GARDENER;
            case 3:
                return RobotType.LUMBERJACK;
            case 4:
                return RobotType.SOLDIER;
            case 5:
                return RobotType.SCOUT;
            case 6:
                return RobotType.TANK;
            default:
                return null;
        }
    }

    static void donate(){
    	if (rc.getTeamBullets() >= MAX_STOCKPILE_BULLET){
    		try {
				//rc.donate((float)(Math.floor((rc.getTeamBullets() - MAX_STOCKPILE_BULLET) / 10.0) * 10.0));
				rc.donate(DONATION_AMT);
			} catch (GameActionException e) {
				e.printStackTrace();
			}
    	}
    }

	//ABS func radians
	static float absRad(float r){
		if(r>DEG_360) return r-DEG_360;
		else if(r<0) return r+DEG_360;
		else return r;
	}

	//ABS sensor array length
    static int absIdx(int i, int n){
	    if (i>=n) return i-n;
	    else if (i<0) return i+n;
	    else return i;
    }

}
