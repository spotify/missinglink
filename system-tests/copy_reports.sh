#!/bin/bash
for dir in $(ls tests); do
  cp tests/$dir/target/missinglink-reports/report.json tests/$dir/expected.json
done
