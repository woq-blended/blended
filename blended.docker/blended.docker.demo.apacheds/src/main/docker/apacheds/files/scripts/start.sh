#!/bin/bash

set -e
set -x

export DOMAIN_NAME=blended
export SYSTEM_PWD=blended

APACHE_DS_VERSION=2.0.0_M24

function shaPassword() {
  pwd=`echo -n $1 | md5sum | awk '{print $1}' | xxd -r -p | base64`
  export HASHED_PWD=`echo -n "{MD5}${pwd}" | base64`
}

function stopADS() {
  /etc/init.d/apacheds-${APACHE_DS_VERSION}-default stop
}

function startADS() {

  if [[ -n "$1" ]]; then
    START_MODE=$1
  else
    START_MODE=start
  fi

  /etc/init.d/apacheds-${APACHE_DS_VERSION}-default $START_MODE

  if [[ -n $2 ]]; then
    sleep $2
  fi
}

function restartADS() {
  stopADS
  startADS $*
}

function loadLdif() {
  envsubst < /opt/apacheds/ldif/$2.ldif > /tmp/$2.ldif
  ldapmodify -c -a -f /tmp/$2.ldif -h localhost -p 10389 -D "uid=admin,ou=system" -w $1
}

function addUser {
  export USER=$1
  shift

  export USER_CN=$1
  shift

  export USER_SN=$1
  shift

  shaPassword $1
  export USER_PWD=$HASHED_PWD
  shift

  loadLdif $SYSTEM_PWD user
}

# Initially start the LDAP server
startADS start 5

# then we change the admin password
#export HASHED_PWD=$SYSTEM_PWD
shaPassword $SYSTEM_PWD
loadLdif secret admin_pwd

# Restart to apply changes
restartADS start 5

# create a new partition
loadLdif $SYSTEM_PWD partition
ldapdelete "ads-partitionId=example,ou=partitions,ads-directoryServiceId=default,ou=config" -r -p 10389 -h localhost -D "uid=admin,ou=system" -w $SYSTEM_PWD
ldapdelete "dc=example,dc=com" -p 10389 -h localhost -D "uid=admin,ou=system" -r -w $SYSTEM_PWD

restartADS start 5

# create the top level entries
loadLdif $SYSTEM_PWD top_domain
loadLdif $SYSTEM_PWD top_objects

addUser root "Main Admin" Administrator mysecret
addUser andreas "Andreas Gies" Gies mysecret
addUser tobias "Tobias Roeser" Roeser mysecret

restartADS console

