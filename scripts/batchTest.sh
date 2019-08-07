#!/bin/bash

BATCHSIZE=3
mkdir -p target

function projects() {
  projects=()
  for p in $(sbt projects | sed "s,\x1B\[[0-9;]*[a-zA-Z],,g" | grep "  blended" | sed s/[^b]*//) ; do
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
  cmd=('coverageOn ')
  for ((idx = 0 ; idx < $sem ; idx++)) ; do
    cmd+=$(echo "${projects[$idx]}/test ")
  done

  echo "${cmd[@]}"
}

# First get all the projects and count them
p=($(projects))
prjCount=${#p[@]}
batchCount=$(($prjCount / $BATCHSIZE))

rc=0
# for ((i=0 ; i <= $batchCount ; i++)) ; do
for ((i=0 ; i < 1 ; i++)) ; do
  prj=($(getBatch $i ${p[@]}))
  if [ ${#prj[@]} -gt 0 ] ; then
    cmd=$(echo "sbt $(testBatchCmd ${prj[@]})")
    eval $cmd
    brc=$?
    if [ $brc -gt $rc ] ; then
      rc=$brc
    fi
  fi
done

exit $rc
