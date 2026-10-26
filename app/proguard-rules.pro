# kotlinx.serialization ships its own R8 rules for @Serializable classes; nothing here needs reflection.
# Keep line numbers so a crash report from the phone still points at the right source line.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
