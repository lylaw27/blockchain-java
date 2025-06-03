# Proof of Work Blockchain in Java

## Overview
This project implements a Proof of Work (PoW) blockchain in Java with the following key features:
- Peer-to-peer communication using gRPC between nodes
- Mempool for transaction management
- UTXO model for processing transactions
- Transaction validation via P2PKH (Pay To Public Key Hash)
- Comprehensive block validation
- Consensus mechanism based on PoW (Proof Of Work)
- Nodes with mining capabilities to extract transactions with highest fees
- Chain reorganization handling
- Blockchain Explorer (Frontend Project) to view the current state of the blockchain

## Features

### 1. Node-to-Node Communication (gRPC)
- #### Uses gRPC for efficient node communication
- #### Protocol buffers define message formats for blocks, transactions, and network messages
```proto
syntax = "proto3";

option java_package = "com.example.modular_blockchain";
option java_multiple_files = true;

service Node{
  rpc HandleTransaction(Transaction) returns (Void){};
  rpc Handshake(Version) returns (Version){};
  rpc HandleBlock(Block) returns (Void){};
  rpc HandleBalance(WalletInfo) returns (UTXOList){};
  rpc HandleWallet(WalletInfo) returns (Void){}
  rpc RequestBlock(BlockIndex) returns (Void){};
}

message Version {
  uint32 version = 1;
  int32 height = 2;
  string listenAddr = 3;
  repeated string peer = 4;
}

message Void{ }

message Block{
  Header header = 1;
  repeated Transaction transactions = 2;
}

message Header{
  string prevHash = 1;
  string merkleRoot = 2;
  int64 timestamp = 3;
  uint32 nonce = 4;
  int32 difficulty = 5;
}

message TxInput{
  string prevTxHash = 1;
  optional uint32 prevOutIndex = 2;
  string publicKey = 3;
  string signature = 4;
  optional bool coinbase = 5;
}

message TxOutput{
  optional int64 amount = 1;
  string address = 2;
}

message Transaction {
  string TxID = 1;
  repeated TxInput inputs = 2;
  repeated TxOutput outputs = 3;
}

message UTXOList{
  repeated UTXO utxos = 1;
}

message UTXO{
  TxOutput output = 1;
  string ID = 2;
}

message WalletInfo{
  string address = 1;
}

message BlockIndex{
  Version version = 1;
  int32 index = 2;
}
```

- #### Supports peer discovery and network propagation
```java
//Initiate connection to a node
public boolean connect(String peer){

        //Setup gRPC Channel
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", Integer.parseInt(peer)).usePlaintext().build();
        NodeGrpc.NodeBlockingStub stub = NodeGrpc.newBlockingStub(channel);

        //Prepare Peer List
        peers.forEach((k,v)->{
            version.addPeer(v.getListenAddr());
        });
        try{
            //Attempt to connect to peer
            Version peerVer = stub.handshake(version.build());

            //Add peer to peer list
            addPeer(peerVer);

            //Check if peer has a longer chain, if yes request for blocks
            checkIncomingHeight(peerVer);
        }
        catch (StatusRuntimeException e){
            System.out.println("Peer does not exist");
            return false;
        }
        finally{
            channel.shutdown();
        }
        return true;
    }

    //Add peer to the peer list and connect to other peers in the peer list
    public void addPeer(Version peerVer){

        //Add peer to peer list
        peers.putIfAbsent(peerVer.getListenAddr(),peerVer);
        System.out.println(version.getListenAddr() + " connected to peer " + peerVer.getListenAddr());

        //Peer Discovery - connect to other peers in the peer list
        peerVer.getPeerList().forEach(addr->{

        //Connect to other peers if it is not the same as this node's address and not already in the peer list
        if(!Objects.equals(version.getListenAddr(),addr) && !peers.containsKey(addr)){
            this.connect(addr);
        }});
    }

   //gRPC endpoint for handshake
    @Override
    public void handshake(Version request, StreamObserver<Version> responseObserver){

        //Send information of this node as response 
        responseObserver.onNext(node.getVersion().build());
        responseObserver.onCompleted();

        //Add node that sends incoming request to peer list
        node.addPeer(request);

        //Add all peers of the node that sends incoming request to peer list
        node.getPeers().forEach((k,v)->{
            node.getVersion().addPeer(v.getListenAddr());
        });
    }
```

### 2. Chain Reorganization
- Handles forks in the blockchain
- Automatically switches to the longest valid chain
- Reverts transactions from orphaned blocks and applies new ones

### 3. Mempool System
- Temporarily stores unconfirmed transactions
- Implements transaction prioritization
- Handles duplicate transaction detection
- Supports transaction expiration

### 4. Validation System
- *Block Validation*:
  - Proof of Work difficulty verification
  - Merkle root validation
  - Block size limits
  - Timestamp validation
  - Previous block hash verification

- *Transaction Validation*:
  - Digital signature verification
  - Input/output validation
  - Double-spend prevention
  - Fee verification

## Prerequisites
- Java JDK 17 or higher
- Maven 3.6+
- Protobuf compiler (protoc)
- gRPC Java libraries

## Installation
1. Clone the repository:
   bash
   git clone https://github.com/yourusername/pow-blockchain-java.git
   cd pow-blockchain-java
   

2. Build the project:
   bash
   mvn clean install
   

3. Generate gRPC code:
   bash
   mvn compile
   

## Configuration
Edit config.properties to configure:
- Network port
- Peer connections
- Mining difficulty
- Block time target
- Mempool settings

Example configuration:
properties
node.port=50051
peer.nodes=192.168.1.2:50051,192.168.1.3:50051
mining.difficulty=18
block.target.time=60000
mempool.max.size=10000


## Running the Node
Start a node with:
bash
mvn exec:java -Dexec.mainClass="com.blockchain.NodeMain"


### Command Line Options
- --mine: Enable mining mode
- --port: Specify custom port
- --peers: Override configured peers
- --genesis: Initialize new genesis block

## API Documentation
The node exposes gRPC services for:
- Submitting transactions
- Querying blockchain state
- Peer management
- Mining control

See src/main/proto/blockchain.proto for service definitions.

## Architecture

### Core Components
1. *Blockchain Manager*: Maintains the chain state and handles reorgs
2. *Network Service*: Manages gRPC communication
3. *Consensus Engine*: Implements PoW mining
4. *Mempool*: Handles unconfirmed transactions
5. *Validation Engine*: Validates blocks and transactions

### Data Structures
- Block: Contains header and transaction list
- Transaction: Inputs, outputs, and signatures
- UTXOSet: Unspent transaction outputs
- PeerList: Known network nodes

## Testing
Run unit tests:
bash
mvn test


Integration tests simulate network behavior with multiple nodes.

## Contributing
1. Fork the repository
2. Create a feature branch
3. Submit a pull request

## License
MIT License

## Future Work
- Wallet integration
- Smart contract support
- Performance optimizations
- Additional consensus mechanisms

## Contact
For questions or issues, please open a GitHub issue or contact maintainers.
