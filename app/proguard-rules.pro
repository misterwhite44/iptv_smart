# Annotations de compilation utilisées par Tink (security-crypto), absentes à l'exécution.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# Classe chargée par son nom depuis le manifeste par le framework Cast.
-keep class com.misterwhite.iptvsmart.cast.CastOptionsProvider { *; }
