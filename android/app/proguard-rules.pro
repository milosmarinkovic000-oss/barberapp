# Čitljivi izveštaji o padu (Crashlytics mapira imena preko mapping fajla).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Ostala pravila dolaze iz biblioteka (consumer rules): Firebase, Hilt, kotlinx.serialization
# (tipizovane rute navigacije), Coil i Compose. Aplikacija ne koristi refleksiju nad svojim klasama
# (Firestore dokumenti se mapiraju ručno), pa dodatna -keep pravila nisu potrebna.
