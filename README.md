# datomisca-dao [![Build Status](https://travis-ci.org/Enalmada/datomisca-dao.svg?branch=master)](https://travis-ci.org/Enalmada/datomisca-dao) [![Join the chat at https://gitter.im/Enalmada/datomisca-dao](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/Enalmada/datomisca-dao?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.enalmada/datomisca-dao/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.enalmada/datomisca-dao)

Datomisca Dao helper code.

#### Version information
* `2.4.0` to `2.5.x` (last: `0.160` - [master branch](https://github.com/enalmada/datomisca-dao/tree/master))

Releases are on [mvnrepository](http://mvnrepository.com/artifact/com.github.enalmada) and snapshots can be found on [sonatype](https://oss.sonatype.org/content/repositories/snapshots/com/github/enalmada).

## Quickstart
Clone the project and run `sbt run` to see a sample application.

### Including the Dependencies

```xml
<dependency>
    <groupId>com.github.enalmada</groupId>
    <artifactId>datomisca-dao_2.12</artifactId>
    <version>0.1.19</version>
</dependency>
```
or

```scala
val appDependencies = Seq(
  "com.github.enalmada" %% "datomisca-dao" % "0.1.19"
)
```

## Features
* Most of the obvious crud operations working
* Sample project shows examples of: crud

## Versions
* **TRUNK** [not released in the repository, yet]
  * Fancy contributing something? :-)
* **0.2.0** [release on 2020-10-05]
* support scala 2.13
* **0.1.18** [release on 2024-11-09]
* datomic 1.0.7260
* **0.1.17** [release on 2019-12-29]
* default hashcode by id
* **0.1.16** [release on 2019-12-24]
* Playframework 2.8.0   
* **0.1.15** [release on 2019-12-8]
* Playframework 2.7.4 
* **0.1.6** [release on 2017-5-31]
* Add some createEntity updateEntity that async save and return 
* **0.1.5** [release on 2016-08-16]
* Fix bug in sorting of lists when using pageBySort
* **0.1.4** [release on 2016-04-12]
* Add schema feature
* **0.1.0** [release on 2016-02-20]
  * Initial release.

## TODO (help out!)

  
## License

Copyright (c) 2016 Adam Lane

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this work except in compliance with the License. You may obtain a copy of the License in the LICENSE file, or at:

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
  
