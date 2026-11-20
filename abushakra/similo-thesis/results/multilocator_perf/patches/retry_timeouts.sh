#!/bin/bash
# usage: retry_timeouts.sh wayback | asg
# Waits for the 5-minute pass of resume_mlvp.sh to finish, then reruns only the sites that timed out (they are not in the dump),
# with a 10-minute limit. The 5-minute timeout lists are kept as mlvp*.timeouts.5min; sites that hit 10 minutes are logged again in mlvp*.timeouts.
H=/Users/stocco/repos/tools/hybrid-work
case "$1" in
 wayback)
  PROG=$H/dev2/out/sv/B/mlvp.progress; DONE="wayback done"
  until grep -q "$DONE" $PROG 2>/dev/null; do sleep 120; done
  T=$H/dev2/out/sv/B/mlvp.timeouts; [ -f $T ] && mv $T $T.5min
  echo "$(date +%m-%d\ %H:%M) retry pass (10 min limit) starts: $(tr '\n' ' ' < $T.5min)" >> $PROG
  LIMIT=600 $H/resume_mlvp.sh wayback ;;
 asg)
  PROG=$H/ob/out/mlvp.progress; DONE="asg_consec done"
  until grep -q "$DONE" $PROG 2>/dev/null; do sleep 120; done
  for P in f2a consec; do T=$H/ob/out/mlvp.$P.timeouts; [ -f $T ] && mv $T $T.5min; done
  echo "$(date +%m-%d\ %H:%M) retry pass (10 min limit) starts" >> $PROG
  LIMIT=600 $H/resume_mlvp.sh asg_f2a; LIMIT=600 $H/resume_mlvp.sh asg_consec ;;
 *) echo "usage: $0 wayback|asg"; exit 2;;
esac
