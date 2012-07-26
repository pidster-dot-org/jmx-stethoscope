/*
 *  Copyright 2012 The original authors
 *  
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package jmx;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import javax.management.JMX;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

/**
 * @author <a href="http://github.com/pidster">pidster</a>
 *
 */
public class Stethoscope {

    private static final class MapKeyEntrySetComparator implements
            Comparator<Entry<String, Object>> {
        @Override
        public int compare(Entry<String, Object> o1,
                Entry<String, Object> o2) {
            return o1.getKey().compareTo(o2.getKey());
        }
    }

    private static final class MBeanAttributeInfoComparator implements
            Comparator<MBeanAttributeInfo> {
        @Override
        public int compare(MBeanAttributeInfo one,
                MBeanAttributeInfo two) {
            return one.getName().compareTo(two.getName());
        }
    }

    protected static final String LOCAL_CONNECTOR_ADDRESS = "com.sun.management.jmxremote.localConnectorAddress";    

    private static VirtualMachine machine = null;
    private static JMXConnector connector = null;

    public static void main(String[] args) {
        
        try {
            if (args == null || args.length == 0) {
                usage();
            }
            
            else if (args.length == 1 && "--list".equalsIgnoreCase(args[0])) {
                list();
            }
            else if (args.length == 1 && ("--help".equalsIgnoreCase(args[0]) || "help".equalsIgnoreCase(args[0]))) {
                help();
            }
            else if (args.length == 1 && "--usage".equalsIgnoreCase(args[0])) {
                usage();
            }
            
            else if (args.length == 2 && "--console".equalsIgnoreCase(args[1])) {
                console(args[0]);
            }
            else if (args.length >= 2 && "--threads".equalsIgnoreCase(args[1])) {
                long delay = 1000;
                int count = Integer.MAX_VALUE;
                if (args.length >= 3) {
                    delay = Long.parseLong(args[2]);
                }
                if (args.length >= 4) {
                    count = Integer.parseInt(args[3]);
                }
                threads(args[0], delay, count);
            }
            else if (args.length >= 2 && "--get".equalsIgnoreCase(args[1])) {
                String[] dest = new String[args.length - 2];
                System.arraycopy(args, 2, dest, 0, args.length -2);
                get(args[0], dest);
            }
            else if (args.length == 2 && "--info".equalsIgnoreCase(args[1])) {
                info(args[0]);
            }
            else if (args.length == 2 && "--system".equalsIgnoreCase(args[1])) {
                system(args[0]);
            }
            
            else if (args.length == 3 && "--mbeans".equalsIgnoreCase(args[1])) {
                mbeans(args[0], args[2]);
            }

            else {
                usage();
            }
        }
        finally {
            disconnect();
        }
    }
    
    public static void console(String id) {
        try {
            connect(id);
            Console console = System.console();
            String input = null;
            LOOP: while ((input = console.readLine("> ")) != null) {
                if ("exit".equalsIgnoreCase(input) || "quit".equalsIgnoreCase(input)) {
                    break LOOP;
                }
                
                if (input == null || "".equals(input))
                    continue;
                
                if (input.startsWith("get")) {
                    String line = input.replaceFirst("get", "").trim();
                    get(line.split("[ \\s\\t]+"));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void mbeans(String id, String query) {
        try {
            connect(id);
            
            if (query == null || "".equals(query)) {
                System.out.println("Which mbeans? Try using a wildcard, e.g. *:type=*, or *:type=*,*");
                return;
            }

            MBeanServerConnection connection = connector
                    .getMBeanServerConnection();
            ObjectName queryName = new ObjectName(query);
            Set<ObjectName> names = connection.queryNames(queryName, null);
            
            if (names.size() == 0) {
                System.out.printf("Found 0 results %n");
                return;
            }
            
            TreeSet<ObjectName> sorted = new TreeSet<ObjectName>(new Comparator<ObjectName>() {
                @Override
                public int compare(ObjectName one, ObjectName two) {
                    String a = String.format("%s:%s", one.getDomain(), one.getKeyPropertyListString());
                    String b = String.format("%s:%s", two.getDomain(), two.getKeyPropertyListString());
                    return a.compareTo(b);
                }});
            
            sorted.addAll(names);
            
            for (ObjectName name : sorted) {
                System.out.printf(" %s:%s %n", name.getDomain(), name.getKeyPropertyListString());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    
    public static void get(String id, String[] args) {
        try {
            connect(id);
            get(args);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void get(String[] args) {
        try {
            
            if (args == null || args.length == 0 || (args.length == 1 && "".equals(args[0]))) {
                System.out.println("'get' what? Try using a wildcard, e.g. *:type=*");
                return;
            }
            
            String beanName = args[0];

            MBeanServerConnection connection = connector.getMBeanServerConnection();
            ObjectName query = new ObjectName(beanName);
            Set<ObjectName> names = connection.queryNames(query, null);
            
            if (names.size() == 0) {
                System.out.printf("Found 0 results %n");
                return;
            }
            
            TreeSet<ObjectName> sorted = new TreeSet<ObjectName>(new Comparator<ObjectName>() {
                @Override
                public int compare(ObjectName one, ObjectName two) {
                    String a = String.format("%s:%s", one.getDomain(), one.getKeyPropertyListString());
                    String b = String.format("%s:%s", two.getDomain(), two.getKeyPropertyListString());
                    return a.compareTo(b);
                }});

            sorted.addAll(names);

            for (ObjectName name : sorted) {
                MBeanInfo info = connection.getMBeanInfo(name);
                
                if (args.length == 2) {
                    String attrName = args[1];
                    Object o = connection.getAttribute(name, attrName);
                    System.out.printf("%s:%s.%s=%s%n", name.getDomain(), name.getKeyPropertyListString(), attrName, o);
                }
                else {
                    System.out.printf(" %s:%s %n", name.getDomain(),
                            name.getKeyPropertyListString());

                    MBeanAttributeInfo[] attributes = info.getAttributes();
                    Arrays.sort(attributes, new MBeanAttributeInfoComparator());

                    for (MBeanAttributeInfo attr : attributes) {
                        Object o = null;
                        try {
                            o = connection.getAttribute(name, attr.getName());
                        } catch (Exception e) {
                            Throwable t = e;
                            while (t.getCause() != null) {
                                t = t.getCause();
                            }
                            o = t.getMessage();
                        }
                        
                        System.out.printf("  %s=%s%n", attr.getName(), o);                        
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void threads(String id, long delay, int count) {
        try {
            connect(id);
            MBeanServerConnection connection = connector.getMBeanServerConnection();
            
            ObjectName threading = new ObjectName(ManagementFactory.THREAD_MXBEAN_NAME);
            ThreadMXBean threadMXBean = JMX.newMXBeanProxy(connection, threading, ThreadMXBean.class);

            boolean first = true;
            
            for (int i=0; i<count; i++) {

                Map<String, Object> data = new HashMap<String, Object>();
                
                data.put("DTC", threadMXBean.getDaemonThreadCount());
                data.put("PTC", threadMXBean.getPeakThreadCount());
                data.put("TC", threadMXBean.getThreadCount());
                data.put("STC", threadMXBean.getTotalStartedThreadCount());

                long[] findDeadlockedThreads = threadMXBean.findDeadlockedThreads();
                int deadlockedThreadCount = (findDeadlockedThreads != null) ? findDeadlockedThreads.length : 0;
                data.put("DLC", deadlockedThreadCount);
                
                long[] findMonitorDeadlockedThreads = threadMXBean.findMonitorDeadlockedThreads();
                int monitorDeadlockedThreads = (findMonitorDeadlockedThreads != null) ? findMonitorDeadlockedThreads.length : 0;
                data.put("MDLC", monitorDeadlockedThreads);
                

                ObjectName query = new ObjectName("*:type=Executor,name=*");
                Set<ObjectName> names = connection.queryNames(query, null);
                
                if (names.size() >= 0) {
                    TreeSet<ObjectName> sorted = new TreeSet<ObjectName>(new Comparator<ObjectName>() {
                        @Override
                        public int compare(ObjectName one, ObjectName two) {
                            String a = String.format("%s:%s", one.getDomain(), one.getKeyPropertyListString());
                            String b = String.format("%s:%s", two.getDomain(), two.getKeyPropertyListString());
                            return a.compareTo(b);
                        }});

                    sorted.addAll(names);
                    
                    int e = 0;
                    for (ObjectName name : sorted) {
                        data.put("x" + e + "AC", connection.getAttribute(name, "activeCount"));
                        data.put("x" + e + "CPS", connection.getAttribute(name, "corePoolSize"));
                        data.put("x" + e + "LPS", connection.getAttribute(name, "largestPoolSize"));
                        data.put("x" + e + "PS", connection.getAttribute(name, "poolSize"));
                        data.put("x" + e + "QS", connection.getAttribute(name, "queueSize"));
                        e++;
                    }
                }

                StringBuilder header = new StringBuilder();
                StringBuilder row = new StringBuilder();
                Set<Entry<String, Object>> entrySet = new TreeSet<Entry<String, Object>>(new MapKeyEntrySetComparator());
                entrySet.addAll(data.entrySet());
                
                
                for (Entry<String, Object> e : entrySet) {
                    header.append(String.format(" %-8s", e.getKey()));
                    row.append(String.format(" %-8s", e.getValue()));
                }
                
                // row.append("%n");
                
                if (first) {
                    System.out.printf("Displaying thread info for PID: %s %n", id);
                    System.out.println(header.toString());
                }
                System.out.println(row.toString());
                                
                Thread.sleep(delay);
                first = false;                
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void info(String id) {
        try {
            connect(id);
            
            MBeanServerConnection connection = connector.getMBeanServerConnection();

            ObjectName operatingSystem = new ObjectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
            OperatingSystemMXBean osMXBean = JMX.newMXBeanProxy(connection, operatingSystem, OperatingSystemMXBean.class);
            
            ObjectName runtime = new ObjectName(ManagementFactory.RUNTIME_MXBEAN_NAME);
            RuntimeMXBean runtimeMXBean = JMX.newMXBeanProxy(connection, runtime, RuntimeMXBean.class);
            
            long startTime = runtimeMXBean.getStartTime();
            long uptime = System.currentTimeMillis() - startTime;
            
            Date started = new Date(startTime);
            
            System.out.printf("Connected to PID:%s uid:%s %n", id, runtimeMXBean.getName());
            System.out.printf("%s (v%s) [%s - %s procs] %n", osMXBean.getName(), osMXBean.getVersion(), osMXBean.getArch(), osMXBean.getAvailableProcessors());
            System.out.printf("load av:%s uptime:%sms started:%s %n", osMXBean.getSystemLoadAverage(), uptime, started);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void system(String id) {
        try {
            connect(id);
            
            MBeanServerConnection connection = connector.getMBeanServerConnection();
            
            ObjectName runtime = new ObjectName(ManagementFactory.RUNTIME_MXBEAN_NAME);
            RuntimeMXBean runtimeMXBean = JMX.newMXBeanProxy(connection, runtime, RuntimeMXBean.class);
            
            System.out.printf("Connected to PID: %s %n", id);

            Map<String, String> systemProperties = runtimeMXBean.getSystemProperties();
            Set<Entry<String, String>> entrySet = systemProperties.entrySet();
            
            for (Entry<String, String> entry : entrySet) {
                System.out.printf("%s=%s %n", entry.getKey(), entry.getValue());
            }
            
            
        } catch (AttachNotSupportedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (MalformedObjectNameException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }
    
    
    public static void list() {
        System.out.println("Listing Java processes visible to this user: ");
        List<VirtualMachineDescriptor> descriptors = VirtualMachine.list();
        for (VirtualMachineDescriptor vmd : descriptors) {
            System.out.printf("%6s %s %n", vmd.id(), vmd.displayName());
        }
    }
    
    public static void help() {
        System.out.println("There's no help (or hope), see --usage");
    }

    public static void usage() {
        System.out.println("Usage: scope [--help | --list | <pid> [--console | --info | --system | --threads | --mbeans <namequery> | --get <namequery> [<attr>]]]");
    }

    private static void connect(String id) throws AttachNotSupportedException,
            IOException, MalformedURLException {
        machine = VirtualMachine.attach(id);
        String javaHome = System.getProperty("java.home");

        if (!machine.getAgentProperties().contains(LOCAL_CONNECTOR_ADDRESS)) {
            String agent = String.format("%1$s%2$slib%2$smanagement-agent.jar",
                    javaHome, File.separator);

            try {
                machine.loadAgent(agent);
            } catch (AgentLoadException e) {
                e.printStackTrace();
            } catch (AgentInitializationException e) {
                e.printStackTrace();
            }
        }

        String serviceURL = machine.getAgentProperties().getProperty(
                LOCAL_CONNECTOR_ADDRESS);
        Map<String, Object> properties = new HashMap<String, Object>();

        JMXServiceURL jmxURL = new JMXServiceURL(serviceURL);
        connector = JMXConnectorFactory.newJMXConnector(jmxURL, properties);

        connector.connect();
    }
    
    private static void disconnect() {
        if (machine != null)
            try {
                machine.detach();
            } catch (IOException e) {
                e.printStackTrace();
            }
        if (connector != null)
            try {
                connector.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
    }
}
