# -*- coding: utf-8 -*-

import sys
import os

# Import the common config file
# Note that paths in the common config are interpreted as if they were 
# in the location of this file

# Setup the config
sys.path.insert(0, os.path.abspath('../../_common'))
from common_conf import * 

html_short_title_toc, html_short_title, html_context = set_conf_for_manual()

if release:
    rst_epilog += """
.. |literal-Wordcount-release-jar| replace:: ``WordCount-%(release)s.jar``

""" % {'release': release}
