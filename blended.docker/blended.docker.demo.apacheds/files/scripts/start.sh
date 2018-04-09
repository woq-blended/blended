#!/bin/bash

export ADMIN_PWD=blended
export DOMAIN_NAME=blended

APACHE_DS_VERSION=2.0.0_M24

set -x

function stopADS {
  /etc/init.d/apacheds-${APACHE_DS_VERSION}-default stop
}

function startADS {

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

function restartADS {
  stopADS
  startADS $*
}

function loadLdif {
  envsubst < /opt/apacheds/ldif/$2.ldif > /tmp/$2.ldif
  ldapmodify -c -a -f /tmp/$2.ldif -h localhost -p 10389 -D "uid=admin,ou=system" -w $1
}

# Initially start the LDAP server
startADS start 5

# then we change the admin password
loadLdif secret admin_pwd

# Restart to apply changes
restartADS start 5

# create a new partition
envsubst < /opt/apacheds/ldif/top_domain.ldif > /tmp/top_domain.ldif
export TOP_CTXT=$(base64 -w 0 /tmp/top_domain.ldif)
loadLdif $ADMIN_PWD partition
ldapdelete "ads-partitionId=example,ou=partitions,ads-directoryServiceId=default,ou=config" -r -p 10389 -h localhost -D "uid=admin,ou=system" -w $ADMIN_PWD
ldapdelete "dc=example,dc=com" -p 10389 -h localhost -D "uid=admin,ou=system" -r -w $ADMIN_PWD

restartADS start 5

# create the top level entries
loadLdif $ADMIN_PWD top_domain
loadLdif $ADMIN_PWD top_objects

restartADS console
