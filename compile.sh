#!/bin/bash

# Répertoires
TEMP_SRC="temp_src"
MY_CLASSES="classes"

# Création du répertoire temporaire
# Vérifier si le répertoire de destination existe
if [ -d "$TEMP_SRC" ]; then
    # Si le répertoire de destination existe, supprimez-le
    echo "Le répertoire de destination existe déjà. Suppression en cours..."
    rm -r "$TEMP_SRC"
    echo "Répertoire de destination supprimé avec succès."
fi
mkdir -p "$TEMP_SRC"
echo "Répertoire temporaire pour source créé"

# Création du répertoire pour les classes compilées
# Vérifier si le répertoire de destination existe
if [ -d "$MY_CLASSES" ]; then
    # Si le répertoire de destination existe, supprimez-le
    echo "Le répertoire de destination existe déjà. Suppression en cours..."
    rm -r "$MY_CLASSES"
    echo "Répertoire de destination supprimé avec succès."
fi
mkdir -p "$MY_CLASSES"
echo "Répertoire temporaire pour les .class créé"

# Copier les fichiers .java vers le répertoire temporaire
find "src" -name "*.java" -exec cp {} "$TEMP_SRC" \;

# Compilation des fichiers Java du répertoire source vers le répertoire des classes
javac -d "$MY_CLASSES" "$TEMP_SRC"/*.java
echo "Fichiers Java compilés dans le répertoire classes"

# Création du fichier jar
jar cf "lib/front_Servlet.jar" -C "$MY_CLASSES" .
echo "Fichier jar créé avec succès."

# Suppression des répertoires temporaires
rm -r "$TEMP_SRC" "$MY_CLASSES"
echo "Répertoires temporaires $TEMP_SRC et $MY_CLASSES supprimés."
