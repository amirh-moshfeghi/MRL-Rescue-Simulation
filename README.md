<!-- ⚠️ This README has been generated from the file(s) "blueprint.md" ⚠️--><p align="center">
  <img src="https://spl.robocup.org/wp-content/uploads/images/Teams2009/MRL-Logo.gif" alt="Logo" width="350" height="150" />
</p>
<h1 align="center">@amirh-moshfeghi/MRL-Rescue-Simulation</h1>

<p align="center">
  <b>This is the simple instruction to run and customize  simulation platform </b></br>
  <p align="left">Use this instruction below so you can run the optimize version of rescue simulation platform which has been awarded many times in Robocup throughout past years.you can <code>contribute</code> to this project and please dont hesitate to ask any kind of question.<br>
at the end of the instruction you can read the brief version of what our platform do overall and how our simulation works<sub>
</p>

<br />


<p align="center">
  <img src="https://www.robocup.org/system/sub_leagues/images/000/000/026/list/ressim.png?1473153988" alt="Demo" width="800" />
</p>

* **Fire Brigades**: red lighting agents in the map who are responsible to search for civilians since 2020(past years they were responsible to search and distinguish fire)
* **Police Force**: blue lighting agents in the map who are responsible to clear blockades or search for civilians 
* **Ambulance Team**: white lighting agents in the map who are responsible to load and drop civilians to the refuge so the civilians health would have been restored
* **Blockades**: black objects in the map which is the symbol of crashed buildings
* **Buildings**: rectangular shaped or any similar objects in the map
* **Civilians**: the green lighting agents in the map,they are the symbol of humans trapped in collapse
<details>
<summary>📖 Table of Contents</summary>
<br />


## ➤ Table of Contents

* [➤ Pre-Requisites]
* [➤ Installation]
* [➤ Compile]
* [➤ Execute and Run]
* [➤ Usage]
* [➤ How Rescue Simulation Works]
* [➤ Contributors]
* [➤ License]

</details>

[![-----------------------------------------------------](https://raw.githubusercontent.com/andreasbm/readme/master/assets/lines/colored.png)](#installation)

## ➤ Pre-Requisites

```
- Git
- Gradle
- OpenJDK Java 11+
```

These are same Pre-Requisites for both Linux and Windows

[![-----------------------------------------------------](https://raw.githubusercontent.com/andreasbm/readme/master/assets/lines/colored.png)](#installation)

## ➤ Installation

```bash
$ git clone https://github.com/roborescue/rcrs-adf-sample.git
```

First you have to install ADF(Agent Development Framework) which is written in java and it is for robocup competitions purpose(you are free to use it anywhere)
[![-----------------------------------------------------](https://raw.githubusercontent.com/andreasbm/readme/master/assets/lines/colored.png)](#getting-started-quick)

## ➤ Compile

```bash
$ ./gradlew clean

$ ./gradlew build
```

[![-----------------------------------------------------](https://raw.githubusercontent.com/andreasbm/readme/master/assets/lines/colored.png)](#getting-started-slower)

## ➤ Execute and Run

The `rcrs-adf-sample` is a sample team implementation for the RCRS (`rcrs-server`) using the ADF core (`rcrs-adf-core`).

To run the `rcrs-adf-sample`, first the `rcrs-server` must be running (Instructions of how to download, compile and run the `rcrs-server` are available at <https://github.com/roborescue/rcrs-server>).

After start the `rcrs-server`, open a new terminal window and execute

```bash
$ cd MRL

$ ./launch.sh -all
```

[![-----------------------------------------------------](https://raw.githubusercontent.com/andreasbm/readme/master/assets/lines/colored.png)](#getting-started-slower)

### ➤ Usage

The instruction Table is listed below,if you want to read more you can visit: https://rescuesim.robocup.org/resources/documentation

There might be some changes in below table in the last version.if you ran into any problem you can contact me anytime

| Option                | Type                                             | Description                                      |
|-----------------------|--------------------------------------------------|--------------------------------------------------|
| -p, --package         | string                                           | Path of the 'package.json' file. Defaults to 'package.json'. |
| --pkg.contributors    | {name: string; email: string; url: string; img: string; info: string[];}[] | Contributors of the project. Used for the 'contributors' . |
| --pkg.license         | string                                           | License kind. Used for the 'license' .   |
| -o, --output          | string                                           | Path of the generated Log file |
| -h, --help            |                                                  | Display this help message.                       |
| -s, --silent          | boolean                                          | Whether the console output from the command should be silent. |
| -d, --dry             | boolean                                          | Whether the command should run as dry. If dry, the output file is notgenerated but outputted to the console instead. |
| --headingPrefix       | {[key: number]: string}                          | The prefix of the header tags. Defaults to '{1: "➤ ", 2: "➤ "}' |
| --extend              | string                                           | Path to another configuration object that should be extended. |



<br>
<br>


### ➤ How Rescue Simulation Works
<sub>
our system is a java platform which has a specific architecture so we can develop every section as easiest as possible.we use GIS to create a map first and we make some simulators of collision and fire and blockades and so on.then we use clustering algorithms like k-means and assign agents to them with hungarian algorithm.i am assuming that you know how to implement agents or you are using pre-defined agent from ADF.let' s consider an example how to make a search for an agent (calculation of search):

```java
@Override
public PathPlanning calc() {
List<EntityID> open = new LinkedList<>();
List<EntityID> close = new LinkedList<>();
Map<EntityID, Node> nodeMap = new HashMap<>();
open.add(this.from);
nodeMap.put(this.from, new Node(null, this.from));
close.clear();

    while (true) {
      if (open.size() < 0) {
        this.result = null;
        return this;
      }
      Node n = null;
      for (EntityID id : open) {
        Node node = nodeMap.get(id);
        if (n == null) {
          n = node;
        } else if (node.estimate() < n.estimate()) {
          n = node;
        }
      }
      if (targets.contains(n.getID())) {
        List<EntityID> path = new LinkedList<>();
        while (n != null) {
          path.add(0, n.getID());
          n = nodeMap.get(n.getParent());
        }
        this.result = path;
        return this;
      }
      open.remove(n.getID());
      close.add(n.getID());

      Collection<EntityID> neighbours = this.graph.get(n.getID());
      for (EntityID neighbour : neighbours) {
        Node m = new Node(n, neighbour);
        if (!open.contains(neighbour) && !close.contains(neighbour)) {
          open.add(m.getID());
          nodeMap.put(neighbour, m);
        }
        else if (open.contains(neighbour) && m.estimate() < nodeMap.get(neighbour).estimate()) {
          nodeMap.put(neighbour, m);
        }
        else if (!close.contains(neighbour) && m.estimate() < nodeMap.get(neighbour).estimate()) {
          nodeMap.put(neighbour, m);
        }
      }
    }
}

```


<br>
as you see our approach is to simulate a city or something like that and then we try to rescue civilians so our agents have to calculate and decide what is the best way and optimize way to rescue all civilians in minimum time

</sub>



## ➤ Contributors


| [<img alt="Amir Moshfeghi" src="https://avatars.githubusercontent.com/u/92248573?s=40&v=4" width="60">](https://amirmoshfegh.com) |  
|:--------------------------------------------------:|
| [Amirhossein  Moshfeghi](https://www.linkedin.com/in/amir-moshfeghi) |  



### License



[![-----------------------------------------------------](https://raw.githubusercontent.com/andreasbm/readme/master/assets/lines/colored.png)](#license)

## ➤ License

Licensed under [MIT](https://opensource.org/licenses/MIT).






