# Proguard rules for Rev Hunter
-keep class com.jcraft.jsch.** { *; }
-keep interface com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

-keep class com.hunter.reborn.** { *; }
-dontwarn com.hunter.reborn.**

-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
