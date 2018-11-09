#!/bin/sh

ONESHOT=""
if [ -n "$SD2_DM_ONESHOT" ]; then
    ONESHOT=" -t"
fi

TIMEOUT="1h"
if [ -n "$SD2_DM_TIMEOUT" ]; then
    TIMEOUT=$SD2_DM_TIMEOUT
fi

TIMEOUT_CMD=""
if [ -n "$SD2_DM_KILL_AFTER_TIMEOUT" ]; then
    TIMEOUT_CMD="timeout -k 5 --preserve-status ${TIMEOUT} "
fi

CMD="${TIMEOUT_CMD}java -jar SD2MaintainDictionary-all.jar -l ${SD2_DM_LOGIN} -p ${SD2_DM_PASSWORD} -s ${SD2_DM_SLEEP} -c ${SD2_DM_COLLECTION} -S ${SD2_DM_SYNBIOHUB_SERVER} -g ${SD2_DM_GSHEET_ID}${ONESHOT}"

if [ -n "${ONESHOT}" ]; then
    while true;
    do
        echo "Launching SD2 Dictionary Maintainer on $(date)" | tee -a run.log
        ${CMD}
        ec=$?
        if [ "$ec" -gt 0 ]; then
            echo "** Error or hung process (> ${TIMEOUT}), had to terminate **" | tee -a run.log
        fi
        sleep 60
    done
else
    ${CMD}
fi
