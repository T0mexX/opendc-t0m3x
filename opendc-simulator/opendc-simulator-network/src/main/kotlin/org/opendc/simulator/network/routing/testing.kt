package org.opendc.simulator.network.routing

import org.opendc.simulator.network.simscope.NetSimScope

internal suspend fun main() {

    val scope = NetSimScope()

    val mngr = scope.addrMngr

//    mngr.getNewIp()
//    mngr.getNewIp()
//    mngr.getNewIp()
//    mngr.getNewIp()
//    mngr.getNewIp()
    val sub1 = mngr.getNewSubNet(nIps = 8)
    println(sub1)

    val sub2 = mngr.getNewSubNet(nIps = 16)

    val sub3 = mngr.getNewSubNet(sub1, nIps = 4)
    val sub4 = mngr.getNewSubNet(sub3, nIps = 2)
    mngr.getNewIp(sub4)
    mngr.getNewIp(sub4)
    mngr.getNewIp(sub4)
//
//    mngr.getNewIp(subNet = sub1)
//    mngr.getNewIp(subNet = sub1)
//    mngr.getNewIp(subNet = sub2)
//    mngr.getNewIp()
//    val sub3 = mngr.getNewSubNet(nIps = 4, of = sub1)

//    val network = IPv4AddressNetwork()
//    val addr = network.addressCreator.createAddress(
//        arrayOf(
//            IPv4AddressSegment(1),
//            IPv4AddressSegment(2),
//            IPv4AddressSegment(3),
//            IPv4AddressSegment(4),
//        ),
//        5,
//    )
//
//    val trie = IPv4AddressTrie()
//
//    val addressBits = 32
//    val requiredPrefixLength = (addressBits - ceil(log2(4.toDouble()))).toInt()
//    println("requiredPrefixLength: $requiredPrefixLength")
//
//    val fullBlock = IPAddressString("0.0.0.0/0").address.toPrefixBlock()
//    val targetBlock = IPAddressString("0.0.0.0/$requiredPrefixLength").address.toPrefixBlock()
//
//    // Iterate over all possible /prefixLength blocks in IPv4
//    for (candidate in fullBlock.spanWithPrefixBlocks(targetBlock)) {
//        println(candidate)
//    }




}
