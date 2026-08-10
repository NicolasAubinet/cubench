package com.cube.nanotimer.services.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.cube.nanotimer.vo.CubeType;
import com.cube.nanotimer.vo.R;
import com.cube.nanotimer.vo.ScrambleType;
import com.cube.nanotimer.vo.TimerQuickAction;

public class DBHelper extends SQLiteOpenHelper {

  protected static SQLiteDatabase db;
  private Context context;

  public DBHelper(Context context) {
    this(context, DB.DB_NAME);
  }

  public DBHelper(Context context, String dbName) {
    super(context, dbName, null, DB.DB_VERSION);
    this.context = context;
    if (db == null) {
      db = getWritableDatabase();
    }
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    DBHelper.db = db;
    createTables(db);
    insertDefaultValues();
  }

  public void createTables(SQLiteDatabase db) {
    db.execSQL("CREATE TABLE " + DB.TABLE_CUBETYPE + "(" +
        DB.COL_ID + " INTEGER PRIMARY KEY, " +
        DB.COL_CUBETYPE_NAME + " TEXT NOT NULL " +
      ");"
    );

    db.execSQL("CREATE TABLE " + DB.TABLE_SOLVETYPE + "(" +
        DB.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        DB.COL_SOLVETYPE_NAME + " TEXT NOT NULL, " +
        DB.COL_SOLVETYPE_POSITION + " INTEGER DEFAULT 0, " +
        DB.COL_SOLVETYPE_BLIND + " INTEGER DEFAULT 0, " +
        DB.COL_SOLVETYPE_INSPECTION + " INTEGER DEFAULT 1, " +
        DB.COL_SOLVETYPE_METHOD + " TEXT, " +
        DB.COL_SOLVETYPE_SCRAMBLE_TYPE + " TEXT, " +
        DB.COL_SOLVETYPE_QUICK_ACTION + " INTEGER, " + // NULL to follow TimerQuickAction.getDefault
        DB.COL_SOLVETYPE_CUBETYPE_ID + " INTEGER, " +
        "FOREIGN KEY (" + DB.COL_SOLVETYPE_CUBETYPE_ID + ") REFERENCES " + DB.TABLE_CUBETYPE + " (" + DB.COL_ID + ") " +
      ");"
    );

    db.execSQL("CREATE TABLE " + DB.TABLE_TIMEHISTORY + "(" +
        DB.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        DB.COL_TIMEHISTORY_TIMESTAMP + " INTEGER, " +
        DB.COL_TIMEHISTORY_TIME + " INTEGER, " +
        DB.COL_TIMEHISTORY_TIME_BEFORE_DNF + " INTEGER, " +
        DB.COL_TIMEHISTORY_SCRAMBLE + " TEXT, " +
        DB.COL_TIMEHISTORY_COMMENT + " TEXT, " +
        DB.COL_TIMEHISTORY_AVG5 + " INTEGER, " +
        DB.COL_TIMEHISTORY_AVG12 + " INTEGER, " +
        DB.COL_TIMEHISTORY_AVG50 + " INTEGER, " +
        DB.COL_TIMEHISTORY_AVG100 + " INTEGER, " +
        DB.COL_TIMEHISTORY_PLUSTWO + " INTEGER DEFAULT 0, " +
        DB.COL_TIMEHISTORY_PB + " INTEGER DEFAULT 0, " +
        DB.COL_TIMEHISTORY_SMARTCUBE_METHOD + " TEXT, " +
        DB.COL_TIMEHISTORY_SMARTCUBE_MOVES + " TEXT, " +
        DB.COL_TIMEHISTORY_SMARTCUBE_GYRO + " TEXT, " +
        DB.COL_TIMEHISTORY_SMARTCUBE_STOPPED_STEP + " INTEGER, " +
        DB.COL_TIMEHISTORY_SOLVETYPE_ID + " INTEGER, " +
        "FOREIGN KEY (" + DB.COL_TIMEHISTORY_SOLVETYPE_ID + ") REFERENCES " + DB.TABLE_SOLVETYPE + " (" + DB.COL_ID + ") " +
      ");"
    );

    db.execSQL("CREATE TABLE " + DB.TABLE_SOLVETYPESTEP + "(" +
        DB.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        DB.COL_SOLVETYPESTEP_NAME + " TEXT, " +
        DB.COL_SOLVETYPESTEP_POSITION + " INTEGER NOT NULL, " +
        DB.COL_SOLVETYPESTEP_SOLVETYPE_ID + " INTEGER, " +
        "FOREIGN KEY (" + DB.COL_SOLVETYPESTEP_SOLVETYPE_ID + ") REFERENCES " + DB.TABLE_SOLVETYPE + " (" + DB.COL_ID + ") " +
      ");"
    );

    db.execSQL("CREATE TABLE " + DB.TABLE_TIMEHISTORYSTEP + "(" +
        DB.COL_TIMEHISTORYSTEP_TIME + " INTEGER, " +
        DB.COL_TIMEHISTORYSTEP_SOLVETYPESTEP_ID + " INTEGER, " +
        DB.COL_TIMEHISTORYSTEP_TIMEHISTORY_ID + " INTEGER, " +
        "FOREIGN KEY (" + DB.COL_TIMEHISTORYSTEP_SOLVETYPESTEP_ID + ") REFERENCES " + DB.TABLE_SOLVETYPESTEP + " (" + DB.COL_ID + "), " +
        "FOREIGN KEY (" + DB.COL_TIMEHISTORYSTEP_TIMEHISTORY_ID + ") REFERENCES " + DB.TABLE_TIMEHISTORY + " (" + DB.COL_ID + ") " +
      ");"
    );

    db.execSQL("CREATE TABLE " + DB.TABLE_SMARTCUBE_SOLVESTEP + "(" +
        DB.COL_SMARTCUBE_SOLVESTEP_STEP_INDEX + " INTEGER NOT NULL, " +
        DB.COL_SMARTCUBE_SOLVESTEP_SUB_INDEX + " INTEGER, " +
        DB.COL_SMARTCUBE_SOLVESTEP_NAME + " TEXT, " +
        DB.COL_SMARTCUBE_SOLVESTEP_TIME + " INTEGER NOT NULL, " +
        DB.COL_SMARTCUBE_SOLVESTEP_RECOGNITION + " INTEGER NOT NULL, " +
        DB.COL_SMARTCUBE_SOLVESTEP_TIMEHISTORY_ID + " INTEGER, " +
        "FOREIGN KEY (" + DB.COL_SMARTCUBE_SOLVESTEP_TIMEHISTORY_ID + ") REFERENCES " + DB.TABLE_TIMEHISTORY + " (" + DB.COL_ID + ") " +
      ");"
    );
    db.execSQL("CREATE INDEX " + DB.IDX_SMARTCUBE_SOLVESTEP_TIMEHISTORY +
        " ON " + DB.TABLE_SMARTCUBE_SOLVESTEP + " (" + DB.COL_SMARTCUBE_SOLVESTEP_TIMEHISTORY_ID + ");"
    );

    db.execSQL("CREATE TABLE " + DB.TABLE_SESSION + "(" +
        DB.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        DB.COL_SESSION_START + " INTEGER NOT NULL, " +
        DB.COL_SESSION_SOLVETYPE_ID + " INTEGER, " +
        "FOREIGN KEY (" + DB.COL_SESSION_SOLVETYPE_ID + ") REFERENCES " + DB.TABLE_SOLVETYPE + " (" + DB.COL_ID + ") " +
      ");"
    );

    createDrillTables(db);
  }

  /**
   * The three drill tables, from {@link #createTables} and from the upgrade that introduced them.
   * Shared rather than written twice: the older tables here are duplicated between the two and the
   * copies are what a schema change has to remember to touch.
   */
  private void createDrillTables(SQLiteDatabase db) {
    db.execSQL("CREATE TABLE " + DB.TABLE_DRILL + "(" +
        DB.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        DB.COL_DRILL_TIMESTAMP + " INTEGER NOT NULL, " +
        DB.COL_DRILL_SPEC + " TEXT NOT NULL, " +
        DB.COL_DRILL_SPEC_ID + " TEXT, " +
        DB.COL_DRILL_TYPE + " TEXT NOT NULL, " +
        DB.COL_DRILL_REPS_ASKED + " INTEGER NOT NULL, " +
        DB.COL_DRILL_END + " INTEGER " + // NULL for a drill the app was killed in the middle of
      ");"
    );

    db.execSQL("CREATE TABLE " + DB.TABLE_DRILL_REP + "(" +
        DB.COL_DRILL_REP_DRILL_ID + " INTEGER NOT NULL, " +
        DB.COL_DRILL_REP_POSITION + " INTEGER NOT NULL, " +
        DB.COL_DRILL_REP_CASE + " TEXT NOT NULL, " +
        DB.COL_DRILL_REP_SCRAMBLE + " TEXT, " +
        DB.COL_DRILL_REP_MOVES + " TEXT, " +
        DB.COL_DRILL_REP_RECOGNITION + " INTEGER NOT NULL, " +
        DB.COL_DRILL_REP_EXECUTION + " INTEGER NOT NULL, " +
        DB.COL_DRILL_REP_MOVE_COUNT + " INTEGER NOT NULL, " +
        DB.COL_DRILL_REP_RESET_COUNT + " INTEGER NOT NULL DEFAULT 0, " +
        DB.COL_DRILL_REP_REVEALED + " INTEGER NOT NULL DEFAULT 0, " +
        DB.COL_DRILL_REP_ABANDONED + " INTEGER NOT NULL DEFAULT 0, " +
        DB.COL_DRILL_REP_DELETED + " INTEGER NOT NULL DEFAULT 0, " +
        "FOREIGN KEY (" + DB.COL_DRILL_REP_DRILL_ID + ") REFERENCES " + DB.TABLE_DRILL + " (" + DB.COL_ID + ") " +
      ");"
    );
    db.execSQL("CREATE INDEX " + DB.IDX_DRILL_REP_DRILL +
        " ON " + DB.TABLE_DRILL_REP + " (" + DB.COL_DRILL_REP_DRILL_ID + ");"
    );
    // The per-case tally is what this table is shaped for, and it reads across drills rather than
    // within one, so it gets an index of its own.
    db.execSQL("CREATE INDEX " + DB.IDX_DRILL_REP_CASE +
        " ON " + DB.TABLE_DRILL_REP + " (" + DB.COL_DRILL_REP_CASE + ");"
    );

    db.execSQL("CREATE TABLE " + DB.TABLE_DRILL_CROSS_REP + "(" +
        DB.COL_DRILL_CROSS_REP_DRILL_ID + " INTEGER NOT NULL, " +
        DB.COL_DRILL_CROSS_REP_POSITION + " INTEGER NOT NULL, " +
        DB.COL_DRILL_CROSS_REP_FACE + " TEXT NOT NULL, " +
        DB.COL_DRILL_CROSS_REP_SCRAMBLE + " TEXT, " +
        DB.COL_DRILL_CROSS_REP_MOVES + " TEXT, " +
        DB.COL_DRILL_CROSS_REP_PLANNING + " INTEGER NOT NULL, " +
        DB.COL_DRILL_CROSS_REP_EXECUTION + " INTEGER NOT NULL, " +
        DB.COL_DRILL_CROSS_REP_MOVE_COUNT + " INTEGER NOT NULL, " +
        DB.COL_DRILL_CROSS_REP_OPTIMAL_LENGTH + " INTEGER NOT NULL DEFAULT 0, " +
        DB.COL_DRILL_CROSS_REP_BUILT + " INTEGER NOT NULL DEFAULT 1, " +
        DB.COL_DRILL_CROSS_REP_PLANNING_EXPIRED + " INTEGER NOT NULL DEFAULT 0, " +
        "FOREIGN KEY (" + DB.COL_DRILL_CROSS_REP_DRILL_ID + ") REFERENCES " + DB.TABLE_DRILL + " (" + DB.COL_ID + ") " +
      ");"
    );
    db.execSQL("CREATE INDEX " + DB.IDX_DRILL_CROSS_REP_DRILL +
        " ON " + DB.TABLE_DRILL_CROSS_REP + " (" + DB.COL_DRILL_CROSS_REP_DRILL_ID + ");"
    );
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    if (DBHelper.db == null) {
      DBHelper.db = db;
    }

    /*ProgressDialog progressDialog = new ProgressDialog(context);
    progressDialog.setMessage(getString(R.string.updating_database));
    progressDialog.setIndeterminate(true);
    progressDialog.setCancelable(false);
    progressDialog.show();*/

    if (oldVersion < 9) {
      // Add Square-1 and Clock
      insertSolveType(getString(R.string.def), insertCubeType(10, getString(R.string.square1)));
      insertSolveType(getString(R.string.def), insertCubeType(11, getString(R.string.clock)));

      // Add avg50 column and calculate values for it
      db.execSQL("ALTER TABLE " + DB.TABLE_TIMEHISTORY + " ADD COLUMN " + DB.COL_TIMEHISTORY_AVG50 + " INTEGER");
      DBUpgradeScripts.calculateAndUpdateAvg50(db);
    }

    if (oldVersion < 10) {
      // Add new blind solve type mode
      db.execSQL("ALTER TABLE " + DB.TABLE_SOLVETYPE + " ADD COLUMN " + DB.COL_SOLVETYPE_BLIND + " INTEGER DEFAULT 0");

      // Set blind mode to all solve types containing "Blind" or "BLD" in their names and that do not have any steps
      DBUpgradeScripts.updateSolveTypesToBlindType(db);

      // Update all averages to the new style (from means (dropping DNF's) to averages (counting DNF's)) + BLD mean of 3
      DBUpgradeScripts.updateMeansToAverages(db);
    }

    if (oldVersion < 11) {
      // Add new pb column (indicates if it's a new record)
      db.execSQL("ALTER TABLE " + DB.TABLE_TIMEHISTORY + " ADD COLUMN " + DB.COL_TIMEHISTORY_PB + " INTEGER DEFAULT 0");

      // Update personal flag for existing times
      DBUpgradeScripts.updatePersonalBestFlag(db);
    }

    if (oldVersion < 12) {
      // Create new Session table
      db.execSQL("CREATE TABLE " + DB.TABLE_SESSION + "(" +
          DB.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
          DB.COL_SESSION_START + " INTEGER NOT NULL, " +
          DB.COL_SESSION_SOLVETYPE_ID + " INTEGER, " +
          "FOREIGN KEY (" + DB.COL_SESSION_SOLVETYPE_ID + ") REFERENCES " + DB.TABLE_SOLVETYPE + " (" + DB.COL_ID + ") " +
        ");"
      );

      // Move solvetype.sessionstart field to the new session table
      DBUpgradeScripts.updateSessionStarts(db);
    }

    if (oldVersion < 13) {
      // Add "scramble type" column to solve types
      db.execSQL("ALTER TABLE " + DB.TABLE_SOLVETYPE + " ADD COLUMN " + DB.COL_SOLVETYPE_SCRAMBLE_TYPE + " TEXT");
    }

    if (oldVersion < 14) {
      db.execSQL("ALTER TABLE " + DB.TABLE_TIMEHISTORY + " ADD COLUMN " + DB.COL_TIMEHISTORY_COMMENT + " TEXT");
    }

    if (oldVersion < 15) {
      insertSolveType(getString(R.string.def), insertCubeType(12, getString(R.string.fto)));
    }

    if (oldVersion < 16) {
      // Add the step breakdown of smart cube solves, and the method it was solved with
      db.execSQL("CREATE TABLE " + DB.TABLE_SMARTCUBE_SOLVESTEP + "(" +
          DB.COL_SMARTCUBE_SOLVESTEP_STEP_INDEX + " INTEGER NOT NULL, " +
          DB.COL_SMARTCUBE_SOLVESTEP_SUB_INDEX + " INTEGER, " +
          DB.COL_SMARTCUBE_SOLVESTEP_NAME + " TEXT, " +
          DB.COL_SMARTCUBE_SOLVESTEP_TIME + " INTEGER NOT NULL, " +
          DB.COL_SMARTCUBE_SOLVESTEP_RECOGNITION + " INTEGER NOT NULL, " +
          DB.COL_SMARTCUBE_SOLVESTEP_TIMEHISTORY_ID + " INTEGER, " +
          "FOREIGN KEY (" + DB.COL_SMARTCUBE_SOLVESTEP_TIMEHISTORY_ID + ") REFERENCES " + DB.TABLE_TIMEHISTORY + " (" + DB.COL_ID + ") " +
        ");"
      );
      db.execSQL("CREATE INDEX " + DB.IDX_SMARTCUBE_SOLVESTEP_TIMEHISTORY +
          " ON " + DB.TABLE_SMARTCUBE_SOLVESTEP + " (" + DB.COL_SMARTCUBE_SOLVESTEP_TIMEHISTORY_ID + ");"
      );

      db.execSQL("ALTER TABLE " + DB.TABLE_TIMEHISTORY + " ADD COLUMN " + DB.COL_TIMEHISTORY_SMARTCUBE_METHOD + " TEXT");
    }

    if (oldVersion < 17) {
      // Add the moves of smart cube solves, kept even when no method matched
      db.execSQL("ALTER TABLE " + DB.TABLE_TIMEHISTORY + " ADD COLUMN " + DB.COL_TIMEHISTORY_SMARTCUBE_MOVES + " TEXT");
    }

    if (oldVersion < 18) {
      // A per-step "complete" flag, replaced by timehistory.smartcube_stopped_step in 19 before it
      // ever shipped. Literal column name: this DDL is frozen, so it cannot follow DB.java.
      db.execSQL("ALTER TABLE " + DB.TABLE_SMARTCUBE_SOLVESTEP + " ADD COLUMN "
          + "complete INTEGER NOT NULL DEFAULT 1");
    }

    if (oldVersion < 19) {
      // Record where an unfinished solve stopped, once per solve rather than once per step.
      db.execSQL("ALTER TABLE " + DB.TABLE_TIMEHISTORY + " ADD COLUMN "
          + DB.COL_TIMEHISTORY_SMARTCUBE_STOPPED_STEP + " INTEGER");

      // Drop 18's "complete" column by rebuilding the table around it: no DROP COLUMN before
      // SQLite 3.35, which is well past minSdk 21. Only a device that ran 18 has the column, but
      // the rebuild is written to run on either shape, so the copy names every column it keeps.
      db.execSQL("ALTER TABLE " + DB.TABLE_SMARTCUBE_SOLVESTEP + " RENAME TO smartcube_solvestep_old");
      db.execSQL("CREATE TABLE " + DB.TABLE_SMARTCUBE_SOLVESTEP + "("
          + "step_index INTEGER NOT NULL, "
          + "sub_index INTEGER, "
          + "name TEXT, "
          + "time INTEGER NOT NULL, "
          + "recognition INTEGER NOT NULL, "
          + "timehistory_id INTEGER, "
          + "FOREIGN KEY (timehistory_id) REFERENCES " + DB.TABLE_TIMEHISTORY + " (" + DB.COL_ID + ") "
          + ");"
      );
      db.execSQL("INSERT INTO " + DB.TABLE_SMARTCUBE_SOLVESTEP
          + " (step_index, sub_index, name, time, recognition, timehistory_id)"
          + " SELECT step_index, sub_index, name, time, recognition, timehistory_id"
          + " FROM smartcube_solvestep_old");
      db.execSQL("DROP TABLE smartcube_solvestep_old"); // takes its index with it
      db.execSQL("CREATE INDEX " + DB.IDX_SMARTCUBE_SOLVESTEP_TIMEHISTORY
          + " ON " + DB.TABLE_SMARTCUBE_SOLVESTEP + " (timehistory_id);"
      );
    }

    if (oldVersion < 20) {
      // Which menu action each solve type puts in the timer's action bar. Blind types get DNF:
      // the scramble the other types show is of no use to a blindfolded solver.
      db.execSQL("ALTER TABLE " + DB.TABLE_SOLVETYPE + " ADD COLUMN "
          + DB.COL_SOLVETYPE_QUICK_ACTION + " INTEGER DEFAULT " + TimerQuickAction.SCRAMBLE_VIEW.getId());
      db.execSQL("UPDATE " + DB.TABLE_SOLVETYPE
          + " SET " + DB.COL_SOLVETYPE_QUICK_ACTION + " = " + TimerQuickAction.DNF.getId()
          + " WHERE " + DB.COL_SOLVETYPE_BLIND + " = 1");
    }

    if (oldVersion < 21) {
      // Remember the time a DNF replaced, so the DNF can be taken back.
      db.execSQL("ALTER TABLE " + DB.TABLE_TIMEHISTORY + " ADD COLUMN "
          + DB.COL_TIMEHISTORY_TIME_BEFORE_DNF + " INTEGER");
    }

    if (oldVersion < 22) {
      // Whether the timer inspects before a solve, now chosen per solve type. Blind types keep the
      // behaviour they always had, inspection having no place before a memorised solve.
      db.execSQL("ALTER TABLE " + DB.TABLE_SOLVETYPE + " ADD COLUMN "
          + DB.COL_SOLVETYPE_INSPECTION + " INTEGER DEFAULT 1");
      db.execSQL("UPDATE " + DB.TABLE_SOLVETYPE
          + " SET " + DB.COL_SOLVETYPE_INSPECTION + " = 0"
          + " WHERE " + DB.COL_SOLVETYPE_BLIND + " = 1");
    }

    if (oldVersion < 23) {
      // Which method a smart cube reads these solves as. Left null for existing types: nothing was
      // ever asked, and no method is the answer that reads the solve rather than presuming it.
      db.execSQL("ALTER TABLE " + DB.TABLE_SOLVETYPE + " ADD COLUMN "
          + DB.COL_SOLVETYPE_METHOD + " TEXT");
    }

    if (oldVersion < 24) {
      // The gyro track of a smart cube solve: the small physical rotations the discrete x/y/z
      // tokens leave out. Solves recorded before this have none and never will — the readings they
      // were reconstructed from were not kept.
      db.execSQL("ALTER TABLE " + DB.TABLE_TIMEHISTORY + " ADD COLUMN "
          + DB.COL_TIMEHISTORY_SMARTCUBE_GYRO + " TEXT");
    }

    if (oldVersion < 25) {
      // New installs inspect in automatic mode from here on. An install that is already going
      // keeps hold and release, a timer starting on another gesture being no small surprise.
      DBUpgradeScripts.keepInspectionModeOfExistingInstall(context);
    }

    if (oldVersion < 26) {
      // NULL now means "follow the default", which version 20 wrote into every row instead. A row
      // still holding the one it was given was never asked the question, so it is cleared.
      db.execSQL("UPDATE " + DB.TABLE_SOLVETYPE
          + " SET " + DB.COL_SOLVETYPE_QUICK_ACTION + " = NULL"
          + " WHERE (" + DB.COL_SOLVETYPE_BLIND + " = 1"
          + "   AND " + DB.COL_SOLVETYPE_QUICK_ACTION + " = " + TimerQuickAction.DNF.getId() + ")"
          + "    OR (" + DB.COL_SOLVETYPE_BLIND + " = 0"
          + "   AND " + DB.COL_SOLVETYPE_QUICK_ACTION + " = " + TimerQuickAction.SCRAMBLE_VIEW.getId() + ")");
    }

    if (oldVersion < 27) {
      // Drill reps, which are recorded apart from solves and never join them.
      createDrillTables(db);
    }

    // Not < 28: a database coming from below 27 was just handed the column by createDrillTables.
    if (oldVersion >= 27 && oldVersion < 28) {
      // A rep the user threw out, flagged rather than deleted so it can be put back.
      db.execSQL("ALTER TABLE " + DB.TABLE_DRILL_REP + " ADD COLUMN "
          + DB.COL_DRILL_REP_DELETED + " INTEGER NOT NULL DEFAULT 0");
    }

//    progressDialog.hide();
  }

  private void insertDefaultValues() {
    final int THREE_BY_THREE_ID = 2;

    insertSolveType(getString(R.string.def), insertCubeType(1, getString(R.string.two_by_two)));
    insertSolveType(getString(R.string.def), insertCubeType(THREE_BY_THREE_ID, getString(R.string.three_by_three)));
    insertSolveType(getString(R.string.def), insertCubeType(3, getString(R.string.four_by_four)));
    insertSolveType(getString(R.string.def), insertCubeType(4, getString(R.string.five_by_five)));
    insertSolveType(getString(R.string.def), insertCubeType(5, getString(R.string.six_by_six)));
    insertSolveType(getString(R.string.def), insertCubeType(6, getString(R.string.seven_by_seven)));
    insertSolveType(getString(R.string.def), insertCubeType(7, getString(R.string.megaminx)));
    insertSolveType(getString(R.string.def), insertCubeType(8, getString(R.string.pyraminx)));
    insertSolveType(getString(R.string.def), insertCubeType(9, getString(R.string.skewb)));
    insertSolveType(getString(R.string.def), insertCubeType(10, getString(R.string.square1)));
    insertSolveType(getString(R.string.def), insertCubeType(11, getString(R.string.clock)));
    insertSolveType(getString(R.string.def), insertCubeType(12, getString(R.string.fto)));

    insertSolveType(getString(R.string.one_handed), THREE_BY_THREE_ID);

    int solveTypeId = insertSolveType(getString(R.string.CFOP_steps), THREE_BY_THREE_ID);
    ContentValues values = new ContentValues();
    values.put(DB.COL_SOLVETYPESTEP_SOLVETYPE_ID, solveTypeId);
    values.put(DB.COL_SOLVETYPESTEP_POSITION, 1);
    values.put(DB.COL_SOLVETYPESTEP_NAME, "Cross");
    db.insert(DB.TABLE_SOLVETYPESTEP, null, values);
    values.put(DB.COL_SOLVETYPESTEP_POSITION, 2);
    values.put(DB.COL_SOLVETYPESTEP_NAME, "F2L");
    db.insert(DB.TABLE_SOLVETYPESTEP, null, values);
    values.put(DB.COL_SOLVETYPESTEP_POSITION, 3);
    values.put(DB.COL_SOLVETYPESTEP_NAME, "OLL");
    db.insert(DB.TABLE_SOLVETYPESTEP, null, values);
    values.put(DB.COL_SOLVETYPESTEP_POSITION, 4);
    values.put(DB.COL_SOLVETYPESTEP_NAME, "PLL");
    db.insert(DB.TABLE_SOLVETYPESTEP, null, values);

    insertSolveType(getString(R.string.last_layer), THREE_BY_THREE_ID, CubeType.THREE_BY_THREE.getScrambleTypeFromString("last_layer"));
  }

  private int insertCubeType(int id, String name) {
    ContentValues values = new ContentValues();
    values.put(DB.COL_ID, id);
    values.put(DB.COL_CUBETYPE_NAME, name);
    return (int) db.insert(DB.TABLE_CUBETYPE, null, values);
  }

  private int insertSolveType(String name, int cubeTypeId) {
    return insertSolveType(name, cubeTypeId, null);
  }

  private int insertSolveType(String name, int cubeTypeId, ScrambleType scrambleType) {
    ContentValues values = new ContentValues();
    values.put(DB.COL_SOLVETYPE_NAME, name);
    values.put(DB.COL_SOLVETYPE_CUBETYPE_ID, cubeTypeId);
    // Spelled out: this also runs from the upgrades that add a puzzle, where the column an older
    // install created still carries version 20's default and would answer for the new type.
    values.putNull(DB.COL_SOLVETYPE_QUICK_ACTION);
    if (scrambleType != null) {
      values.put(DB.COL_SOLVETYPE_SCRAMBLE_TYPE, scrambleType.getName());
    }
    return (int) db.insert(DB.TABLE_SOLVETYPE, null, values);
  }

  private String getString(int resId) {
    return context.getString(resId);
  }

}
