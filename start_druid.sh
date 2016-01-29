#! /bin/bash
 
############################## PRE PROCESSING ################################
#check and process arguments
REQUIRED_NUMBER_OF_ARGUMENTS=1
if [ $# -lt $REQUIRED_NUMBER_OF_ARGUMENTS ]
then
    echo "Usage: $0 <path_to_config_file>"
    exit 1
fi

CONFIG_FILE=$1
 
echo "Config file is $CONFIG_FILE"
echo ""
 
#get the configuration parameters
source $CONFIG_FILE




############################## PROCESS CONFIG FILE ################################
#construct realtime FQDN
NEW_REALTIME_NODE=''
for node in ${REALTIME_NODE//,/ }
do
    if [ "$IP" == "TRUE" -o "$FQDN" == "TRUE" ] 
    then
        REALTIME_NODE_FQDN=$node
    else
        REALTIME_NODE_FQDN=$node.$EXPERIMENT.$PROJ.$ENV
    fi
 
    NEW_REALTIME_NODE=$NEW_REALTIME_NODE$USER_NAME@$REALTIME_NODE_FQDN,
done

#construct broker FQDN
NEW_BROKER_NODES=''
for node in ${BROKER_NODE//,/ }
do
    if [ "$IP" == "TRUE" -o "$FQDN" == "TRUE" ] 
    then
        NEW_BROKER_NODES=$NEW_BROKER_NODES$node,
    else
        NEW_BROKER_NODES=$NEW_BROKER_NODES$USER_NAME@$node.$EXPERIMENT.$PROJ.$ENV,
    fi
done

#construct historical FQDNs
NEW_HISTORICAL_NODES=''
for node in ${HISTORICAL_NODES//,/ }
do
    if [ "$IP" == "TRUE" -o "$FQDN" == "TRUE" ] 
    then
        NEW_HISTORICAL_NODES=$NEW_HISTORICAL_NODES$node,
    else
        NEW_HISTORICAL_NODES=$NEW_HISTORICAL_NODES$USER_NAME@$node.$EXPERIMENT.$PROJ.$ENV,
    fi
done

#construct coordinator FQDNs
NEW_COORDINATOR_NODES=''
for node in ${COORDINATOR_NODE//,/ }
do
    if [ "$IP" == "TRUE" -o "$FQDN" == "TRUE" ] 
    then
        NEW_COORDINATOR_NODES=$NEW_COORDINATOR_NODES$node,
    else
        NEW_COORDINATOR_NODES=$NEW_COORDINATOR_NODES$USER_NAME@$node.$EXPERIMENT.$PROJ.$ENV,
    fi
done

############################## SETUP ################################

#generate keys for passwordless ssh
ssh-keygen; 

#start coordinator FQDN
echo "Setting up coordinator nodes:"
for  node in ${NEW_COORDINATOR_NODE//,/ }
do
	#passwordless ssh
	ssh-copy-id $node;


	#send over necessary files
	rsync -rPz $DRUID_PATH $node:druid-0.9.0-SNAPSHOT;


	#set up zookeeper in the same node as the coordinator
	if ["$ZOOKEEPER" = "TRUE"]
	then
		#have to install curl
		sudo sed -i -e 's/us.archive.ubuntu.com/archive.ubuntu.com/g' /etc/apt/sources.list;
		sudo apt-get update;
		sudo apt-get install curl;


		#have to install zookeeper and then run it
		curl http://www.gtlib.gatech.edu/pub/apache/zookeeper/zookeeper-3.4.6/zookeeper-3.4.6.tar.gz -o zookeeper-3.4.6.tar.gz;
		tar xzf zookeeper-3.4.6.tar.gz;
		cd zookeeper-3.4.6;
		cp conf/zoo_sample.cfg conf/zoo.cfg;
		./bin/zkServer.sh start;
		cd ..;

	fi

  echo "Setting up $node ..."
  COMMAND=''

	COMMAND=$COMMAND"java -Xmx256m -Duser.timezone=UTC -Dfile.encoding=UTF-8 -classpath config/_common:config/coordinator:lib/* io.druid.cli.Main server coordinator"

  if [ "$IP" == "TRUE" ]
  then
    COMMAND=$COMMAND" --bind_ip $node;"
  else
    COMMAND=$COMMAND";"
  fi
  echo "Coordinator node startup command is $COMMAND"

	#ssh to node
	ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no $node;

	#change to bash shell or else command will return java:no match
	/bin/bash;

	#go into druid directory in node
	cd druid-0.9.0-SNAPSHOT;

	#run command
  "$COMMAND"

done
echo ""

#start historical FQDN
counter=0
echo "Setting up historical nodes:"
for  node in ${NEW_HISTORICAL_NODES//,/ }
do
	#passwordless ssh
	ssh-copy-id $node;


	#send over necessary files
	rsync -rPz $DRUID_PATH $node:druid-0.9.0-SNAPSHOT

  echo "Setting up $node ..."
  COMMAND=''

	COMMAND=$COMMAND"java -Xmx256m -Duser.timezone=UTC -Dfile.encoding=UTF-8 -classpath config/_common:config/historical:lib/* io.druid.cli.Main server historical"

  if [ "$IP" == "TRUE" ]
  then
    COMMAND=$COMMAND" --bind_ip $node;"
  else
    COMMAND=$COMMAND";"
  fi
  echo "historical node startup command is $COMMAND"

	#ssh to node
	ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no $node;

	#change to bash shell or else command will return java:no match
	/bin/bash;

	#go into druid directory in node
	cd druid-0.9.0-SNAPSHOT;

	#run command
  "$COMMAND"
done
echo ""

#setup broker FQDN
echo "Setting up broker nodes:"
for  node in ${NEW_BROKER_NODE//,/ }
do
	#passwordless ssh
	ssh-copy-id $node;


	#send over necessary files
	rsync -rPz $DRUID_PATH $node:druid-0.9.0-SNAPSHOT

  echo "Setting up $node ..."
  COMMAND=''

	COMMAND=$COMMAND"java -Xmx256m -Duser.timezone=UTC -Dfile.encoding=UTF-8 -classpath config/_common:config/broker:lib/* io.druid.cli.Main server broker"

  if [ "$IP" == "TRUE" ]
  then
    COMMAND=$COMMAND" --bind_ip $node;"
  else
    COMMAND=$COMMAND";"
  fi
  echo "Realtime node startup command is $COMMAND"

	#ssh to node
	ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no $node;

	#change to bash shell or else command will return java:no match
	/bin/bash;

	#go into druid directory in node
	cd druid-0.9.0-SNAPSHOT;

	#run command
  "$COMMAND"
done
echo ""

#start realtime FQDN
echo "Setting up realtime nodes:"
for  node in ${NEW_REALTIME_NODE//,/ }
do
	#passwordless ssh
	ssh-copy-id $node;


	#send over necessary files
	rsync -rPz $DRUID_PATH $node:druid-0.9.0-SNAPSHOT

  echo "Setting up $node ..."
  COMMAND=''

	COMMAND=$COMMAND"java -Xmx512m -Duser.timezone=UTC -Dfile.encoding=UTF-8 -Ddruid.realtime.specFile=$SPEC_FILE -classpath config/_common:config/realtime:lib/* io.druid.cli.Main server realtime"

  if [ "$IP" == "TRUE" ]
  then
    COMMAND=$COMMAND" --bind_ip $node;"
  else
    COMMAND=$COMMAND";"
  fi
  echo "Realtime node startup command is $COMMAND"

	#ssh to node
	ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no $node;

	#change to bash shell or else command will return java:no match
	/bin/bash;

	#go into druid directory in node
	cd druid-0.9.0-SNAPSHOT;

	#run command
  "$COMMAND"

done
echo ""
