# Readable line numbers in Play crash reports; the bundle's mapping file restores the names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# The model crosses process and version boundaries (Bundles, database, backups, coach payload).
-keep class com.cube.nanotimer.vo.** { *; }
-keep class com.cube.nanotimer.session.** { *; }
-keep class com.cube.nanotimer.drill.** { *; }
-keep class com.cube.nanotimer.coach.** { *; }
-keep class com.cube.nanotimer.step.** { *; }

# Enum names are stored in the database and preferences and read back by name.
-keep enum com.cube.nanotimer.** { *; }

# res/raw/square1_shapes.dat is Java-serialized: the stream names this class and its field.
-keep class com.cube.nanotimer.scrambler.randomstate.square1.Square1State { *; }
