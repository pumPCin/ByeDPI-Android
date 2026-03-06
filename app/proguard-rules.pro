# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class io.github.dovecoteescapee.byedpi.core.ByeDpiProxy { *; }

-keep,allowoptimization class io.github.dovecoteescapee.byedpi.core.TProxyService { *; }
-keep,allowoptimization class io.github.dovecoteescapee.byedpi.activities.** { *; }
-keep,allowoptimization class io.github.dovecoteescapee.byedpi.services.** { *; }
-keep,allowoptimization class io.github.dovecoteescapee.byedpi.receiver.** { *; }

-keep class io.github.dovecoteescapee.byedpi.fragments.** {
    <init>();
}

-keep,allowoptimization class io.github.dovecoteescapee.byedpi.data.** {
    <fields>;
}

-keepattributes Signature
-keepattributes *Annotation*

-repackageclasses 'io.github.dovecoteescapee'
-renamesourcefileattribute ''
-keepattributes SourceFile,InnerClasses,EnclosingMethod,Signature,RuntimeVisibleAnnotations,*Annotation*,*Parcelable*
-allowaccessmodification
-overloadaggressively
-optimizationpasses 5
-verbose
-dontusemixedcaseclassnames
-adaptclassstrings
-adaptresourcefilecontents **.xml,**.json
-adaptresourcefilenames **.xml,**.json
