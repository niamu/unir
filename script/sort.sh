#!/bin/bash

# planck -c $(lein classpath):src -m unir.core $@

lein run -m unir.core $@
