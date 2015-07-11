#!/bin/bash

total=0
failures=0

cd ..

for dir in $(ls tests); do
  pushd tests/$dir

  echo "Running maven in $(pwd)"

  ACTUAL=target/missinglink-reports/report.json
  EXPECTED=expected.json
  diff $ACTUAL $EXPECTED
  if [ $? -ne 0 ]; then
    echo "Comparison failed between $(pwd)/$ACTUAL and $(pwd)/$EXPECTED"
    ((failures=failures+1))
  fi

  ((total=total+1))
  popd
done

echo "Ran $total tests with $failures failures"
if [ $failures -eq 0 ]; then
  exit 0
else
  exit 1
fi
