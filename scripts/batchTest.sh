#!/bin/bash

BATCHSIZE=5
mkdir -p target

function projects() {
  projects=()
  for p in $(sbt projects | grep "  blended" | sed s/[^b]*//) ; do
    projects+="$p "
  done

  unique_projects=($(echo ${projects[@]} | tr " " "\n" | sort -u | tr "\n" " "))
  echo "${unique_projects[@]}"
}

function getBatch() {
  batch=()
  batchNo=$1
  shift
  projects=($@)

  for(( i=0 ; i < $BATCHSIZE ; i++)) ; do
    idx=$(($batchNo * $BATCHSIZE + $i))
    batch+="${projects[$idx]} "
  done

  echo "${batch[@]}"
}

function testBatchCmd() {
  projects=($@)
  sem=${#projects[@]}
  cmd=(';coverageOn;')
  for ((idx = 0 ; idx < $sem ; idx++)) ; do
    cmd+=$(echo "${projects[$idx]}/test;")
  done
    cmd+='exit'

  echo "${cmd[@]}"
}

# First get all the projects and count them
p=($(projects))
prjCount=${#p[@]}
batchCount=$(($prjCount / $BATCHSIZE))

for ((i=0 ; i <= $batchCount ; i++)) ; do
  prj=($(getBatch $i ${p[@]}))
  if [ ${#prj[@]} -gt 0 ] ; then
    echo "$(testBatchCmd ${prj[@]})" > target/cmd.txt
    sbt < target/cmd.txt
  fi
done

