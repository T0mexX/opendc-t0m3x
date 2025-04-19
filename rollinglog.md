


## HammingMesh

##### TOpologies
- Need to add their custom topology *HammingMesh*.

## SimFly
- They use booksim

##### Topologies
- Add *Dragonfly* topology to OpneDCN 
- Maybe also add their topology *Slim Fly*


##### Routing
- *Minimal Static Routing*: basically ospf but with max hops 2?
- *Global UGAL* (UNiversal Globally-Adaptive Load-balanced): can be the first network-aware routing protocol added to OpenDCN
- *Local UGAL*: very easy to add, just a node-aware routing protocol

##### Workloads
- not clear.
- referenced [this](https://dl.acm.org/doi/10.1145/2503210.2503229) , also useful for adding *Jellyfish* topology to OpenDCN



## PolarFly
- Yhey also use booksim

##### Topologies
- Need to add their custom *POlarFly* topology to OpneDCN

##### Workloads
Should be easy to create.
- *Uniform Random Traffic*
- *Random Permutation*
- *Perm1hop*, *Perm2Hop* (UGAL)
