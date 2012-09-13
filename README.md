# JMX Stethoscope

This is a simple tool for inspecting a running JVM process.
It uses the Java Attach API to connect to the process and then provides data from some of the key JMX MBeans.

## Download

You can download a package, from here: [jmx-stethoscope-0.1.tar.gz](https://github.com/pidster/jmx-stethoscope/downloads) — A initial release of jmx-stethoscope-0.1.tar.gz

## Usage

So, now you've got downloaded it, what are you going to do with it?
Running the 'help' command is always worth a try:

    sh jmx-stethoscope-0.1/scope --help

Here's the usage information:

    Usage: scope [--help | --list | <pid> [--console | --info | --system | --threads | --mbeans <namequery> | --get <namequery> [<attr>]]]

### Commands

The following commands are available:

#### --list

Find and list JVM processes available to the current user.

#### --console

Launch an interactive console, the same commands are availble inside the console/

#### <pid> --info

Connect to process id <pid> and execute the 'info' command.

#### <pid> --system

Connect to process id <pid> and execute the 'system' command.

#### <pid> --threads

Connect to process id <pid> and execute the 'threads' command.

#### <pid> --mbeans <namequery>

Connect to process id <pid> and execute the 'mbeans' command.

#### <pid> --get <namequery> [<attr>]

Connect to process id <pid> and execute the 'namequery' command.



