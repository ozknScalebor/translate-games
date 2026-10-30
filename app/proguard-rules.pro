# ML Kit, CameraX and Compose ship their own consumer rules.
# Settings are stored by stable keys rather than enum names, so no extra keep rules are needed.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# LiteRT-LM is driven from native code through JNI.
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
-dontwarn com.google.auto.value.**
-dontwarn javax.annotation.**
