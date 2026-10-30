# ML Kit, CameraX and Compose ship their own consumer rules.
# Settings are stored by stable keys rather than enum names, so no extra keep rules are needed.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# MediaPipe LLM Inference is driven from native code through JNI and protobuf messages.
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**
# ML Kit GenAI (AICore) bindings
-keep class com.google.mlkit.genai.** { *; }
-dontwarn com.google.mlkit.genai.**
-dontwarn com.google.auto.value.**
-dontwarn javax.annotation.**
