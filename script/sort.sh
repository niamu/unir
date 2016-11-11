#!/bin/bash

planck -c $(lein classpath):src -m unir.core $@
