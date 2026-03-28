#!/bin/bash
set -e

# Initialize Rust project
cargo init --name tor-x-core rust/

# Create Kotlin project structure
mkdir -p app/src/main/{kotlin,java,res,AndroidManifest.xml.bak}
mkdir -p app/src/androidTest/kotlin app/src/test/kotlin

# Create gradle wrapper & build files
mkdir -p gradle/wrapper

echo "Project structure created"
