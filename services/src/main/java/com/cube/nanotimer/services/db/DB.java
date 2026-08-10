package com.cube.nanotimer.services.db;

public class DB {

  public static final String DB_NAME = "nanoTimerDB";
  public static final int DB_VERSION = 27;

  public static final String COL_ID = "id";

  public static final String TABLE_CUBETYPE = "cubetype";
  public static final String COL_CUBETYPE_NAME = "name";

  public static final String TABLE_SOLVETYPE = "solvetype";
  public static final String COL_SOLVETYPE_NAME = "name";
//  public static final String COL_SOLVETYPE_SESSION_START = "sessionstart";
  public static final String COL_SOLVETYPE_POSITION = "position";
  public static final String COL_SOLVETYPE_BLIND = "blind";
  public static final String COL_SOLVETYPE_INSPECTION = "inspection";
  public static final String COL_SOLVETYPE_METHOD = "method";
  public static final String COL_SOLVETYPE_SCRAMBLE_TYPE = "scrambletype";
  public static final String COL_SOLVETYPE_QUICK_ACTION = "quickaction";
  public static final String COL_SOLVETYPE_CUBETYPE_ID = "cubetype_id";

  public static final String TABLE_TIMEHISTORY = "timehistory";
  public static final String COL_TIMEHISTORY_TIMESTAMP = "timestamp";
  public static final String COL_TIMEHISTORY_TIME = "time";
  public static final String COL_TIMEHISTORY_TIME_BEFORE_DNF = "time_before_dnf"; // the time a DNF replaced, null when there is none to restore
  public static final String COL_TIMEHISTORY_SCRAMBLE = "scramble";
  public static final String COL_TIMEHISTORY_COMMENT = "comment";
  public static final String COL_TIMEHISTORY_AVG5 = "avg5"; // column also used for "Mean of 3" for blind solve types
  public static final String COL_TIMEHISTORY_AVG12 = "avg12";
  public static final String COL_TIMEHISTORY_AVG50 = "avg50";
  public static final String COL_TIMEHISTORY_AVG100 = "avg100";
  public static final String COL_TIMEHISTORY_PLUSTWO = "plustwo";
  public static final String COL_TIMEHISTORY_PB = "pb";
  public static final String COL_TIMEHISTORY_SMARTCUBE_METHOD = "smartcube_method"; // method code of the solve's step breakdown, null when there is no breakdown (manual solve, or cube solve that matched no method)
  public static final String COL_TIMEHISTORY_SMARTCUBE_MOVES = "smartcube_moves"; // the solve's moves with their offsets (ex: "R@0 U'@180"), null unless a cube drove it, finished or not
  // Several kilobytes on a long solve, so NEVER select it in a query over many rows: only the one
  // solve being looked at ever wants it, and the history list needs no more than moves != null.
  public static final String COL_TIMEHISTORY_SMARTCUBE_GYRO = "smartcube_gyro"; // keyframed orientations, see GyroTrackFormat
  public static final String COL_TIMEHISTORY_SMARTCUBE_STOPPED_STEP = "smartcube_stopped_step"; // index of the step the solve stopped in, null when it ran to the end
  public static final String COL_TIMEHISTORY_SOLVETYPE_ID = "solvetype_id";

  public static final String TABLE_SOLVETYPESTEP = "solvetypestep";
  public static final String COL_SOLVETYPESTEP_NAME = "name";
  public static final String COL_SOLVETYPESTEP_POSITION = "position";
  public static final String COL_SOLVETYPESTEP_SOLVETYPE_ID = "solvetype_id";

  public static final String TABLE_TIMEHISTORYSTEP = "timehistorystep";
  public static final String COL_TIMEHISTORYSTEP_TIME = "time";
  public static final String COL_TIMEHISTORYSTEP_SOLVETYPESTEP_ID = "solvetypestep_id";
  public static final String COL_TIMEHISTORYSTEP_TIMEHISTORY_ID = "timehistory_id";

  public static final String TABLE_SMARTCUBE_SOLVESTEP = "smartcube_solvestep";
  public static final String COL_SMARTCUBE_SOLVESTEP_STEP_INDEX = "step_index";
  public static final String COL_SMARTCUBE_SOLVESTEP_SUB_INDEX = "sub_index"; // null for the step itself, else the position of one of its parts
  public static final String COL_SMARTCUBE_SOLVESTEP_NAME = "name"; // step code (ex: "cross", "pair"), translated when displayed
  public static final String COL_SMARTCUBE_SOLVESTEP_TIME = "time";
  public static final String COL_SMARTCUBE_SOLVESTEP_RECOGNITION = "recognition"; // execution time is the remainder of the step time
  public static final String COL_SMARTCUBE_SOLVESTEP_TIMEHISTORY_ID = "timehistory_id";
  public static final String IDX_SMARTCUBE_SOLVESTEP_TIMEHISTORY = "idx_smartcube_solvestep_timehistory";

  public static final String TABLE_SESSION = "session";
  public static final String COL_SESSION_START = "start";
  public static final String COL_SESSION_SOLVETYPE_ID = "solvetype_id";

  // A drill is not a solve and none of the three tables below touches the ones above. Reps recorded
  // here stay out of the solve history, out of the session averages and out of the method
  // statistics, because those averages are the baseline a drill is supposed to be measured against.
  public static final String TABLE_DRILL = "drill";
  public static final String COL_DRILL_TIMESTAMP = "timestamp";
  public static final String COL_DRILL_SPEC = "spec"; // the whole DrillSpec as its JSON text, so fields a later spec version adds survive
  public static final String COL_DRILL_SPEC_ID = "spec_id"; // the sender's handle for the drill, not unique: the row id is what reps hang off
  public static final String COL_DRILL_TYPE = "type"; // spec type code, so cross drills are told from case ones without parsing the JSON
  public static final String COL_DRILL_REPS_ASKED = "reps_asked";
  public static final String COL_DRILL_END = "end_reason"; // DrillEnd id, NULL for a drill the app was killed in the middle of

  public static final String TABLE_DRILL_REP = "drill_rep";
  public static final String COL_DRILL_REP_DRILL_ID = "drill_id";
  public static final String COL_DRILL_REP_POSITION = "position"; // rows are written as reps finish, so the order is stored rather than implied
  public static final String COL_DRILL_REP_CASE = "case_code"; // as a solve records it: "oll_21", "pll_ga"
  public static final String COL_DRILL_REP_SCRAMBLE = "scramble";
  public static final String COL_DRILL_REP_MOVES = "moves"; // SolveMovesFormat, offsets from the case going up. Tens of bytes: a rep is one algorithm
  public static final String COL_DRILL_REP_RECOGNITION = "recognition";
  public static final String COL_DRILL_REP_EXECUTION = "execution";
  public static final String COL_DRILL_REP_MOVE_COUNT = "move_count";
  public static final String COL_DRILL_REP_RESET_COUNT = "reset_count"; // a time reached on the third go is not a clean one
  public static final String COL_DRILL_REP_REVEALED = "revealed";
  public static final String COL_DRILL_REP_ABANDONED = "abandoned";
  public static final String IDX_DRILL_REP_DRILL = "idx_drill_rep_drill";
  public static final String IDX_DRILL_REP_CASE = "idx_drill_rep_case";

  public static final String TABLE_DRILL_CROSS_REP = "drill_cross_rep";
  public static final String COL_DRILL_CROSS_REP_DRILL_ID = "drill_id";
  public static final String COL_DRILL_CROSS_REP_POSITION = "position";
  public static final String COL_DRILL_CROSS_REP_FACE = "face";
  public static final String COL_DRILL_CROSS_REP_SCRAMBLE = "scramble";
  public static final String COL_DRILL_CROSS_REP_MOVES = "moves";
  public static final String COL_DRILL_CROSS_REP_PLANNING = "planning";
  public static final String COL_DRILL_CROSS_REP_EXECUTION = "execution";
  public static final String COL_DRILL_CROSS_REP_MOVE_COUNT = "move_count";
  public static final String COL_DRILL_CROSS_REP_OPTIMAL_LENGTH = "optimal_length"; // 0 where the search never landed, which is not a suspiciously good rep
  public static final String COL_DRILL_CROSS_REP_BUILT = "built";
  public static final String COL_DRILL_CROSS_REP_PLANNING_EXPIRED = "planning_expired";
  public static final String IDX_DRILL_CROSS_REP_DRILL = "idx_drill_cross_rep_drill";

}
