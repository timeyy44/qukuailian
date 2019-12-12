package org.fisco.bcos.asset.client;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.fisco.bcos.asset.contract.Asset;
import org.fisco.bcos.asset.contract.Asset.RegisterEventEventResponse;
import org.fisco.bcos.asset.contract.Asset.OweEventEventResponse;
import org.fisco.bcos.asset.contract.Asset.ReturnEventEventResponse;
import org.fisco.bcos.asset.contract.Asset.RequireEventEventResponse;
import org.fisco.bcos.channel.client.Service;
import org.fisco.bcos.web3j.crypto.Credentials;
import org.fisco.bcos.web3j.crypto.Keys;
import org.fisco.bcos.web3j.protocol.Web3j;
import org.fisco.bcos.web3j.protocol.channel.ChannelEthereumService;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;
import org.fisco.bcos.web3j.tuples.generated.Tuple2;
import org.fisco.bcos.web3j.tx.gas.StaticGasProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

public class AssetClient {

	static Logger logger = LoggerFactory.getLogger(AssetClient.class);

	private Web3j web3j;

	private Credentials credentials;

	public Web3j getWeb3j() {
		return web3j;
	}

	public void setWeb3j(Web3j web3j) {
		this.web3j = web3j;
	}

	public Credentials getCredentials() {
		return credentials;
	}

	public void setCredentials(Credentials credentials) {
		this.credentials = credentials;
	}

	public void recordAssetAddr(String address) throws FileNotFoundException, IOException {
		Properties prop = new Properties();
		prop.setProperty("address", address);
		final Resource contractResource = new ClassPathResource("contract.properties");
		FileOutputStream fileOutputStream = new FileOutputStream(contractResource.getFile());
		prop.store(fileOutputStream, "contract address");
	}

	public String loadAssetAddr() throws Exception {
		// load Asset contact address from contract.properties
		Properties prop = new Properties();
		final Resource contractResource = new ClassPathResource("contract.properties");
		prop.load(contractResource.getInputStream());

		String contractAddress = prop.getProperty("address");
		if (contractAddress == null || contractAddress.trim().equals("")) {
			throw new Exception(" load Asset contract address failed, please deploy it first. ");
		}
		logger.info(" load Asset address from contract.properties, address is {}", contractAddress);
		return contractAddress;
	}

	public void initialize() throws Exception {

		// init the Service
		@SuppressWarnings("resource")
		ApplicationContext context = new ClassPathXmlApplicationContext("classpath:applicationContext.xml");
		Service service = context.getBean(Service.class);
		service.run();

		ChannelEthereumService channelEthereumService = new ChannelEthereumService();
		channelEthereumService.setChannelService(service);
		Web3j web3j = Web3j.build(channelEthereumService, 1);

		// init Credentials
		Credentials credentials = Credentials.create(Keys.createEcKeyPair());

		setCredentials(credentials);
		setWeb3j(web3j);

		logger.debug(" web3j is " + web3j + " ,credentials is " + credentials);
	}

	private static BigInteger gasPrice = new BigInteger("30000000");
	private static BigInteger gasLimit = new BigInteger("30000000");

	public void deployAssetAndRecordAddr() {

		try {
			Asset asset = Asset.deploy(web3j, credentials, new StaticGasProvider(gasPrice, gasLimit)).send();
			System.out.println(" deploy Asset success, contract address is " + asset.getContractAddress());

			recordAssetAddr(asset.getContractAddress());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			System.out.println(" deploy Asset contract failed, error message is  " + e.getMessage());
		}
	}

	public void queryAssetAmount(String assetAccount) {
		try {
			String contractAddress = loadAssetAddr();

			Asset asset = Asset.load(contractAddress, web3j, credentials, new StaticGasProvider(gasPrice, gasLimit));
			Tuple2<BigInteger, BigInteger> result = asset.select(assetAccount).send();
			if (result.getValue1().compareTo(new BigInteger("0")) == 0) {
				System.out.printf(" asset account %s, value %s \n", assetAccount, result.getValue2());
			} else {
				System.out.printf(" %s asset account is not exist \n", assetAccount);
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			logger.error(" queryAssetAmount exception, error message is {}", e.getMessage());

			System.out.printf(" query asset account failed, error message is %s\n", e.getMessage());
		}
	}

	public void registerAssetAccount(String assetAccount, BigInteger amount) {
		try {
			String contractAddress = loadAssetAddr();

			Asset asset = Asset.load(contractAddress, web3j, credentials, new StaticGasProvider(gasPrice, gasLimit));
			TransactionReceipt receipt = asset.register(assetAccount, amount).send();
			List<RegisterEventEventResponse> response = asset.getRegisterEventEvents(receipt);
			if (!response.isEmpty()) {
				if (response.get(0).ret.compareTo(new BigInteger("0")) == 0) {
					System.out.printf(" register asset account success => asset: %s, value: %s \n", assetAccount,
							amount);
				} else {
					System.out.printf(" register asset account failed, ret code is %s \n",
							response.get(0).ret.toString());
				}
			} else {
				System.out.println(" event log not found, maybe transaction not exec. ");
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();

			logger.error(" registerAssetAccount exception, error message is {}", e.getMessage());
			System.out.printf(" register asset account failed, error message is %s\n", e.getMessage());
		}
	}

	public void oweAssetAccount(String fromAccount, String toAccount, BigInteger amount, BigInteger ddl) {
		try {
			String contractAddress = loadAssetAddr();

			Asset asset = Asset.load(contractAddress, web3j, credentials, new StaticGasProvider(gasPrice, gasLimit));
			TransactionReceipt receipt = asset.owe(fromAccount, toAccount, amount, ddl).send();
			List<OweEventEventResponse> response = asset.getOweEventEvents(receipt);
			if (!response.isEmpty()) {
				if (response.get(0).ret.compareTo(new BigInteger("0")) == 0) {
					System.out.printf(" owe asset account success => asset: %s, value: %s \n", fromAccount,
							amount);
				} else {
					System.out.printf(" owe asset account failed, ret code is %s \n",
							response.get(0).ret.toString());
				}
			} else {
				System.out.println(" event log not found, maybe transaction not exec. ");
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();

			logger.error(" oweAssetAccount exception, error message is {}", e.getMessage());
			System.out.printf(" owe asset account failed, error message is %s\n", e.getMessage());
		}
	}

	public void retuAssetAccount(String fromAccount, String toAccount, BigInteger time) {
		try {
			String contractAddress = loadAssetAddr();

			Asset asset = Asset.load(contractAddress, web3j, credentials, new StaticGasProvider(gasPrice, gasLimit));
			TransactionReceipt receipt = asset.retu(fromAccount, toAccount, time).send();
			List<ReturnEventEventResponse> response = asset.getReturnEventEvents(receipt);
			if (!response.isEmpty()) {
				if (response.get(0).ret.compareTo(new BigInteger("0")) == 0) {
					System.out.printf(" retu asset account success => asset: %s, value: %s \n", fromAccount,
							time);
				} else {
					System.out.printf(" retu asset account failed, ret code is %s \n",
							response.get(0).ret.toString());
				}
			} else {
				System.out.println(" event log not found, maybe transaction not exec. ");
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();

			logger.error(" returAssetAccount exception, error message is {}", e.getMessage());
			System.out.printf(" retu asset account failed, error message is %s\n", e.getMessage());
		}
	}

	public void requAssetAccount(String fromAccount, String toAccount, String lastAccount, BigInteger amount, BigInteger ddl) {
		try {
			String contractAddress = loadAssetAddr();

			Asset asset = Asset.load(contractAddress, web3j, credentials, new StaticGasProvider(gasPrice, gasLimit));
			TransactionReceipt receipt = asset.requ(fromAccount, toAccount, lastAccount, amount, ddl).send();
			List<RequireEventEventResponse> response = asset.getRequireEventEvents(receipt);
			if (!response.isEmpty()) {
				if (response.get(0).ret.compareTo(new BigInteger("0")) == 0) {
					System.out.printf(" requ asset account success => asset: %s, value: %s \n", fromAccount,
							amount);
				} else {
					System.out.printf(" requ asset account failed, ret code is %s \n",
							response.get(0).ret.toString());
				}
			} else {
				System.out.println(" event log not found, maybe transaction not exec. ");
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();

			logger.error(" requAssetAccount exception, error message is {}", e.getMessage());
			System.out.printf(" requ asset account failed, error message is %s\n", e.getMessage());
		}
	}

	public static void Usage() {
		System.out.println(" Usage:");
		System.out.println("\t java -cp conf/:lib/*:apps/* org.fisco.bcos.asset.client.AssetClient deploy");
		System.out.println("\t java -cp conf/:lib/*:apps/* org.fisco.bcos.asset.client.AssetClient query account");
		System.out.println(
				"\t java -cp conf/:lib/*:apps/* org.fisco.bcos.asset.client.AssetClient owe transaction");
		System.out.println(
				"\t java -cp conf/:lib/*:apps/* org.fisco.bcos.asset.client.AssetClient retu transaction");
		System.out.println(
				"\t java -cp conf/:lib/*:apps/* org.fisco.bcos.asset.client.AssetClient requ transaction");
		System.exit(0);
	}

	public static void main(String[] args) throws Exception {

		if (args.length < 1) {
			Usage();
		}

		AssetClient client = new AssetClient();
		client.initialize();

		switch (args[0]) {
		case "deploy":
			client.deployAssetAndRecordAddr();
			break;
		case "query":
			if (args.length < 2) {
				Usage();
			}
			client.queryAssetAmount(args[1]);
			break;
		case "owe":
			if (args.length < 5) {
				Usage();
			}
			client.oweAssetAccount(args[1], args[2], new BigInteger(args[3]), new BigInteger(args[4]));
			break;
		case "register":
			if (args.length < 3) {
				Usage();
			}
			client.registerAssetAccount(args[1], new BigInteger(args[2]));
			break;
		case "retu":
			if (args.length < 4) {
				Usage();
			}
			client.retuAssetAccount(args[1], args[2], new BigInteger(args[3]));
			break;
		case "requ":
			if (args.length < 6) {
				Usage();
			}
			client.requAssetAccount(args[1], args[2], args[3], new BigInteger(args[4]), new BigInteger(args[5]));
			break;
		default: {
			Usage();
		}
		}

		System.exit(0);
	}
}
