#!/bin/sh

find . -name "TEST*.xml" -printf "./dropbox_uploader.sh upload %p travis/$1/%f\n" | sh 
./dropbox_uploader.sh upload target travis/$1/target


