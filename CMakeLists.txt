
cmake_minimum_required(VERSION 3.16)
project(VoiceChanger)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find JNI
find_package(JNI REQUIRED)

# Include directories
include_directories(${JNI_INCLUDE_DIRS})
include_directories(src/main/cpp)
include_directories(src/main/cpp/signalsmith-stretch)
include_directories(src/main/cpp/signalsmith-stretch/plugin)

# Source files
set(SOURCES
    src/main/cpp/VoiceChangerJNI.cpp
    src/main/cpp/VoiceProcessor.cpp
)

# Create shared library
add_library(voicechanger SHARED ${SOURCES})

# Link libraries
target_link_libraries(voicechanger ${JNI_LIBRARIES})

# Set output directory
set_target_properties(voicechanger PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR}/src/main/resources/native
    RUNTIME_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR}/src/main/resources/native
)

# Platform-specific settings
if(WIN32)
    set_target_properties(voicechanger PROPERTIES
        PREFIX ""
        SUFFIX ".dll"
    )
elseif(APPLE)
    set_target_properties(voicechanger PROPERTIES
        PREFIX "lib"
        SUFFIX ".dylib"
    )
else()
    set_target_properties(voicechanger PROPERTIES
        PREFIX "lib"
        SUFFIX ".so"
    )
endif()