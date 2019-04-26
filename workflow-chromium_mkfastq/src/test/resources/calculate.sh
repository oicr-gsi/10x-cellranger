#!/bin/bash

cd $1
find . -type f ! -name "*.zip" -type f ! -name "*.json" -exec md5sum {} +
find . -name "*.json"
