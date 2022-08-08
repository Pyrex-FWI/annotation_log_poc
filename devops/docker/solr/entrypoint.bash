#!/usr/bin/env bash

WORKING_DIR=/var/solr

# Check if missing template folder
DESTINATION_EZ="ezsolr/server/ez"
DESTINATION_TEMPLATE="${DESTINATION_EZ}/template"
if [ ! -d ${DESTINATION_TEMPLATE} ]; then
    cd $WORKING_DIR
    mkdir -p ${DESTINATION_TEMPLATE}
    cp -R /usr/var/www/vendor/ezsystems/ezplatform-solr-search-engine//lib/Resources/config/solr/* ${DESTINATION_TEMPLATE}
fi

# Check for solr config folder (changes btw 6 and 7)
SOURCE_SOLR="/opt/solr/server/solr/configsets/_default/"
if [ ! -d ${SOURCE_SOLR} ]; then
    SOURCE_SOLR="/opt/solr/server/solr/configsets/basic_configs/"
fi

if [ ! -f ${DESTINATION_EZ}/solr.xml ]; then
    cp /opt/solr/server/solr/solr.xml ${DESTINATION_EZ}
    cp ${SOURCE_SOLR}conf/{currency.xml,solrconfig.xml,stopwords.txt,synonyms.txt,elevate.xml} ${DESTINATION_TEMPLATE}
#    cp /ezsolr/currency.xml ${DESTINATION_TEMPLATE}
    sed -i.bak '/<updateRequestProcessorChain name="add-unknown-fields-to-the-schema".*/,/<\/updateRequestProcessorChain>/d' ${DESTINATION_TEMPLATE}/solrconfig.xml
    sed -i -e 's/<maxTime>${solr.autoSoftCommit.maxTime:-1}<\/maxTime>/<maxTime>${solr.autoSoftCommit.maxTime:20}<\/maxTime>/g' ${DESTINATION_TEMPLATE}/solrconfig.xml
    sed -i -e 's/<dataDir>${solr.data.dir:}<\/dataDir>/<dataDir>\/var\/solr\/data\/${solr.core.name}<\/dataDir>/g' ${DESTINATION_TEMPLATE}/solrconfig.xml

fi

CREATE_CORES=false

if [ ! -d ${DESTINATION_EZ}/${SOLR_CORES} ]; then
    CREATE_CORES=true
    echo "Found missing core: ${SOLR_CORES}"
fi

#for core in $SOLR_CORES
#do
#    if [ ! -d ${DESTINATION_EZ}/${SOLR_CORE ];} then
#        {core}
#        CREATE_CORES=true
#        echo "Found missing core: ${SOLR_CORE"}
#    fi
#done

if [ "$CREATE_CORES" = true ]; then
    touch ${DESTINATION_EZ}/solr.creating.cores
    echo "Start solr on background to create missing cores"
    /opt/solr/bin/solr -s ${DESTINATION_EZ}

    if [ ! -d ${DESTINATION_EZ}/${SOLR_CORES} ]; then
        /opt/solr/bin/solr create_core -c ${SOLR_CORES}  -d ${DESTINATION_TEMPLATE}
        echo "Core ${SOLR_CORES} created."
    fi

    echo "Stop background solr"
    /opt/solr/bin/solr stop
    rm ${DESTINATION_EZ}/solr.creating.cores
fi

/opt/solr/bin/solr -s ${DESTINATION_EZ} -f
