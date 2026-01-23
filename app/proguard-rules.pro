-keepattributes SourceFile, LineNumberTable

-keepattributes Signature, Exceptions, *Annotation*
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

-keepclasseswithmembers interface * {
    @retrofit2.http.* <methods>;
}

-keep interface data.YouBikeApiService { *; }

-dontwarn kotlinx.serialization.**
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
    *** Companion;
}

-keep @kotlinx.serialization.Serializable class * { *; }
-keepnames class data.** {
    *** $serializer;
}

-keep class data.** { *; }

-keep class viewmodel.YouBikeUiState { *; }
-keep class viewmodel.StationResult { *; }

-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-dontwarn okhttp3.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory