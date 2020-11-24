## Flink Swift FS plugin with TempAuth

 Original Flink Swift FS plugin doesn't support V1 TempAuth which we need for now for WDQS' Flink based updater. 
 Solution is to modify few key classes to allow different authentication method - all other operation remain the same. 
 
 Code in this repo is mostly a copy from two other repos:
 * Apache Flink (https://github.com/apache/flink/tree/release-1.11/flink-filesystems/flink-swift-fs-hadoop)
 * Apache Hadoop (https://github.com/apache/hadoop/tree/branch-2.8.1/hadoop-tools/hadoop-openstack)
 
 Classes copied are only ones that needed to be modified - either because of the new implementation or class visibility
 restrictions:
  * **SwiftRestClientTempAuth** is a modified copy of **SwiftRestClient**, with TempAuth added as an 
 authentication option. 
 * Other modified classes with **TempAuth** postfix are there to load the TempAuth implementation

Package names are due to the package-private classes from the original plugin. 

Original test had to many dependencies on a Hadoop project, so tests in this repo are new.

### Plugin installation

Plugin need to be added to a Flink installation. To do that, please create a subdirectory of ``plugins`` directory 
(name isn't important). Once you do, copy shaded jar there and restart the cluster.