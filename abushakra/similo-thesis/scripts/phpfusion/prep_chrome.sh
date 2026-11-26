#!/bin/bash
# recreate the upload files the suites reference (author's Desktop/Dropbox paths) inside pf-chrome[K]; usage: prep_chrome.sh [container]
C=${1:-pf-chrome}
cd /Users/stocco/repos/tools/phpfusion-work
docker cp upload/dog-wallpaper-12.jpg $C:/tmp/ && docker cp upload/coffee_beans.png $C:/tmp/
docker exec $C sudo sh -c 'for d in /Users/mouna/Desktop /Dropbox/STUDY2-PHPFusion; do mkdir -p $d; cp /tmp/dog-wallpaper-12.jpg /tmp/coffee_beans.png $d/; done; chmod -R a+rX /Users /Dropbox'
