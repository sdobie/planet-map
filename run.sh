#!/bin/bash
cd "$(dirname "$0")"
javac -d out src/main/java/planetmap/*.java && java -cp out planetmap.PlanetMapApp
