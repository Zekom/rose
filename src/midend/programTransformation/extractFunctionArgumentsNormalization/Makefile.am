include $(top_srcdir)/config/Makefile.for.ROSE.includes.and.libs

INCLUDES = $(ROSE_INCLUDES)
#LINK	 = $(CXXLINK)

# noinst_LTLIBRARIES lists all the libraries that should be built, but not installed
noinst_LTLIBRARIES =  libExtractFunctionArgumentsNormalization.la

libExtractFunctionArgumentsNormalization_la_DEPENDENCIES = 
libExtractFunctionArgumentsNormalization_la_SOURCES = ExtractFunctionArguments.C functionEvaluationOrderTraversal.C
include_HEADERS = ExtractFunctionArguments.h functionEvaluationOrderTraversal.h


# EXTRA_DIST are files that are not compiled or installed. These include readme's, internal header files, etc.
EXTRA_DIST = 

CLEANFILES = 


check-local:

