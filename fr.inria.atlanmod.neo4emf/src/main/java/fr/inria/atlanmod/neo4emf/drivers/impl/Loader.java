package fr.inria.atlanmod.neo4emf.drivers.impl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EFactory;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.xmi.FeatureNotFoundException;
import org.neo4j.graphdb.Node;

import fr.inria.atlanmod.neo4emf.INeo4emfObject;
import fr.inria.atlanmod.neo4emf.INeoFactory;
import fr.inria.atlanmod.neo4emf.change.impl.ChangeLog;
import fr.inria.atlanmod.neo4emf.drivers.ILoader;
import fr.inria.atlanmod.neo4emf.drivers.IPersistenceManager;
import fr.inria.atlanmod.neo4emf.impl.FlatPartition;

public class Loader implements ILoader {
	/**
	 * the manager and delegator
	 */
	IPersistenceManager manager;
	/**
	 * a Map of Loaded packages
	 */
	Map<String,EPackage> ePackageMap; 

	Map<String, Object> defaultOptions;

	public Loader (IPersistenceManager manager){
		this.manager=manager;
		ePackageMap = new  HashMap<String,EPackage>();
	}
	/**
	 * @see ILoader#load(Map)
	 */
	@Override
	public void load(Map<?, ?> options) {
		// merge the options 
		if (options == null)
			options = new HashMap<String, Object>();
		options= mergeWithDefaultOptions(options);
		// TODO introduce the load strategies
		if (((String)options.get(LOADING_STRATEGY)).equals(DYNAMIC_LOADING)){
			dynamicLoad(options);
		} else if (((String)options.get(LOADING_STRATEGY)).equals(FULL_LOADING)) {
			fullLoad(options);
		} else { staticLoad(options); }
	}
	private void fullLoad(Map<?, ?> options) {
		// TODO Auto-generated method stub
		
	}
	private Map<?, ?> mergeWithDefaultOptions(Map<?, ?> options) {
		initOptions();
		for (int i=0; i< loadOptions.length; i++) {
			if (options.containsKey(loadOptions[i])){
				defaultOptions.remove(loadOptions[i]);
				defaultOptions.put(loadOptions[i], options.get(loadOptions[i]));	}
		}
		return this.defaultOptions;
	}
	private void initOptions() {
		this.defaultOptions =  new HashMap<String, Object>();
		for (int i=0;i<loadOptions.length; i++ )
			defaultOptions.put(loadOptions[i], loadDefaultValues[i]);	
	}
	private void staticLoad(Map<?, ?> options) {
		// TODO a meta-partitioning heuristic
		
		
		
	}
	private void dynamicLoad(Map<?,?> options){
		try {
			List <Node> nodes= manager.getAllRootNodes();
			List <INeo4emfObject> objects = new ArrayList<INeo4emfObject>();
			for (Node n : nodes){
				INeo4emfObject obj = getObjectsFromNode(n);
				int newId = manager.getNewPartitionID();
				obj.setPartitionId(newId);
				objects.add(obj);
				manager.createNewPartitionHistory(newId);
			}
			manager.addObjectsToContents(objects);
			manager.putAllToProxy(objects);
		}catch(Exception e) {
			manager.shutdown();
			e.printStackTrace();
		}
	}
	
	
	/**
	 * @see ILoader#getAllInstances(EClass, List)
	 */
	@Override
	public EList<INeo4emfObject> getAllInstances(EClass eClass,
			List<Node> nodeList) {
		int sizeChange = ChangeLog.getInstance().size();
		EList<INeo4emfObject> eObjectList = new BasicEList<INeo4emfObject>();
		int newId = manager.getNewPartitionID();
		FlatPartition partition = manager.createNewFlatPartition(newId);
		for (Node node : nodeList){
			String ns_uri = manager.getNodeContainingPackage(node);
			INeo4emfObject obj = (INeo4emfObject)loadMetamodelFromURI(ns_uri).getEFactoryInstance().create(eClass);
			obj.setNodeId(node.getId());
			obj.setPartitionId(newId);
			eObjectList.add(obj);
			partition.put(obj);
		}
		manager.createNewPartitionHistory(newId);
		manager.addObjectsToContents(eObjectList);
		ChangeLog.getInstance().removeLastChanges(ChangeLog.getInstance().size()-sizeChange);
		return eObjectList;
	}
	
	/**
	 * get an Object from Node 
	 * @param n  {@link Node}
	 * @return {@link INeo4emfObject}
	 */
	protected INeo4emfObject getObjectsFromNode(Node n) {
		String eClassName = manager.getNodeType(n);
		String ns_uri = manager.getNodeContainingPackage(n);
		EPackage ePck = loadMetamodelFromURI(ns_uri);
		int size = ChangeLog.getInstance().size();
		EFactory factory =null;

		if (ePck.getEFactoryInstance() == null) {
			ePck.setEFactoryInstance(INeoFactory.eINSTANCE);
		}
		if (ePck.getEFactoryInstance().getClass().getName()
				.equals("org.eclipse.emf.ecore.impl.EFactoryImpl")) {
			factory = INeoFactory.eINSTANCE;
			factory.setEPackage(ePck);
		} else {
			factory = ePck.getEFactoryInstance();
		}
		INeo4emfObject obj = (INeo4emfObject)factory.create(getEClassFromNodeName(eClassName,ePck));
		obj.setNodeId(n.getId());	
		ChangeLog.getInstance().removeLastChanges(ChangeLog.getInstance().size()-size);
		return obj ;
	}
	/**
	 * Return an {@link EClass} from its class name
	 * @param eClassName {@link String}
	 * @param ePck {@link EPackage}
	 * @return {@link EClass}
	 */
	protected EClass getEClassFromNodeName(String eClassName, EPackage ePck) {

		for (EClassifier eClassifier :  ePck.getEClassifiers()) {
			if (eClassifier instanceof EClass && eClassifier.getName().equalsIgnoreCase(eClassName))
				return (EClass) eClassifier;
		}
		return null;		
	}
	/**
	 * Return the EEnum from the eClass if Exist 
	 * @param enumName {@link String}
	 * @return
	 */
	protected EEnum getEEnumFromNodeName(String enumName){
		for (Map.Entry<String, EPackage> entry : ePackageMap.entrySet()){
			for (EClassifier eClassifier : entry.getValue().getEClassifiers() ){
				if (eClassifier instanceof EEnum && eClassifier.getName().equals(enumName))
					return (EEnum) eClassifier;
			}
		}
		return null;
	}
	/**
	 * Load packages from the  metamodelUri 
	 * @param metamodelURI {@link String}
	 * @return {@link EPackage}
	 */
	protected EPackage loadMetamodelFromURI(String metamodelURI) {
		EPackage metamodel = null;
		if (ePackageMap.containsKey(metamodelURI)) {
			metamodel = ePackageMap.get(metamodelURI);
		}else {
			if (metamodelURI.equals(EcorePackage.eNS_URI)) {
				metamodel = EcorePackage.eINSTANCE;
			}

			else if(EPackage.Registry.INSTANCE.containsKey(metamodelURI)) {
				metamodel = EPackage.Registry.INSTANCE.getEPackage(metamodelURI);
				registerSubPackagesIfExists(metamodel);
			}
		}
		return metamodel;
	}
	/**
	 * register a subPackage if not exists 
	 * @param metamodel
	 */
	private void registerSubPackagesIfExists(EPackage metamodel) {
		ePackageMap.put(metamodel.getNsURI(), metamodel);

		for(EObject object : metamodel.eContents()){
			if (object instanceof EPackage) registerSubPackagesIfExists((EPackage) object);
		}

	}
	/**
	 * fetches attributes of an {@link EObject} from the Node
	 */
	@Override
	public void fetchAttributes(EObject obj, Node n) {
		try{
			
			Object attributeValue= null;
			
			int size = ChangeLog.getInstance().size();
			
			for (EAttribute attr : obj.eClass().getEAllAttributes()) {
			
				attributeValue = n.getProperty(attr.getName());
				Class<?> cls = attributeValue.getClass();
				
				 if (attributeValue.toString().equals("")){ 
						
						if (attr.getEType().getName().equals("Boolean") || attr.getEType().getName().equals("EBoolean")){
							attributeValue=(Boolean)false;
						}
						else if (attr.getEType().getName().equals("String") || attr.getEType().getName().equals("EString")){
							attributeValue=(String)"";
						}
						else {
							 if (attr.getEType().getName().equals("EByte")){
                               attributeValue = new Byte((byte) 0) ;}
                        else if (attr.getEType().getName().equals("EBigInteger"))
                                        attributeValue = new BigInteger("0");
                        else if (attr.getEType().getName().equals("EBigDecimal"))
                                        attributeValue = new BigDecimal("0");
                        else if (attr.getEType().getName().equals("ELong"))
                                attributeValue = Long.parseLong("0");
                        else
							attributeValue=(Integer)0;
						}
						obj.eSet(attr, attributeValue);
					}
				
				 else if (attr.getEType() instanceof EEnum){
					EEnum enumCls = getEEnumFromNodeName(attr.getEType().getName());
					Object enumObject = enumCls.getEPackage().getEFactoryInstance().createFromString(enumCls, (String)attributeValue);
					obj.eSet(attr, enumObject);
				}else if (attr.isMany()){
					obj.eSet(attr,Arrays.asList((List<?>)attributeValue) );
				}
				else{
					obj.eSet(attr, attributeValue);
				}
			}
			ChangeLog.getInstance().removeLastChanges(ChangeLog.getInstance().size() - size);
		}catch (Exception e)
		{	
			manager.shutdown();
			e.printStackTrace();
		}

	}
	/**
	 * Construct the list of links of <b>obj</b> from the node List
	 */
	@Override	
	public void getObjectsOnDemand(EObject obj, int featureId, Node node ,List<Node> nodes) throws FeatureNotFoundException {
		try {
			int size = ChangeLog.getInstance().size();
			int newId = ((INeo4emfObject) obj).getPartitionId();
			
			if (!manager.isHead(obj)) {
				newId = manager.createNewPartition(getObjectsFromNode(node), ((INeo4emfObject) obj).getPartitionId());
				manager.createNewPartitionHistory(newId);
			}
			
			EReference str = Loader.getFeatureFromID(obj, featureId);
			if (str == null) {
				throw new FeatureNotFoundException("", obj, "", -1, -1);
			}
			
			List<INeo4emfObject> objectList = new ArrayList<INeo4emfObject>();
			for (Node n : nodes) {
				INeo4emfObject object = getObjectsFromNodeIfNotExists(obj, n, newId, str.isContainment(), featureId);
				objectList.add(object);
				manager.putToProxy((INeo4emfObject) object, str, newId);
				// manager.updateProxyManager((INeo4emfObject)object, str);
			}

			// TODO: Check this code! It causes movements of objects from their
			// containers to the root of the resource...
			// if (!str.isContainment())
			// manager.addObjectsToContents(objectList);
			if (str.isMany()) {
				obj.eSet(str, objectList);
			} else if (!objectList.isEmpty()) {
				obj.eSet(str, objectList.get(0));
			}
			ChangeLog.getInstance().removeLastChanges(ChangeLog.getInstance().size() - size);
		} catch (FeatureNotFoundException e) {
			e.printStackTrace();
		} catch (Exception e) {
			manager.shutdown();
			e.printStackTrace();
		}

	}

	@Override
	public EObject getContainerOnDemand (EObject eObject, int featureId, Node node, Node containerNode)  {
		EObject result = null;
		int size = ChangeLog.getInstance().size();
		//result = getObjectsFromNodeIfNotExists(eObject, containerNode, ((INeo4emfObject)eObject).getPartitionId(),true);
		int newId=((INeo4emfObject)eObject).getPartitionId();
		if (!manager.isHead(eObject)){
			newId = manager.createNewPartition(getObjectsFromNode(node),((INeo4emfObject)eObject).getPartitionId());
			manager.createNewPartitionHistory(newId);
			}
		result = getObjectsFromNodeIfNotExists(eObject, containerNode, newId, false, featureId);
		ArrayList<INeo4emfObject> arrayResult =new ArrayList<INeo4emfObject>();
		arrayResult.add((INeo4emfObject)result);
		manager.addObjectsToContents(arrayResult);
		manager.putToProxy((INeo4emfObject)result, Loader.getFeatureFromID(eObject, featureId), newId);
		ChangeLog.getInstance().removeLastChanges(ChangeLog.getInstance().size() - size);
		return result;
	}
	
	/**
	 * return the <b>Structural Feature from its ID</b>
	 * @param obj {@link EObject}
	 * @param featureId {@link Int}
	 * @return
	 */
	private static EReference getFeatureFromID(EObject obj,
			int featureId) {
		EStructuralFeature ref = obj.eClass().getEAllStructuralFeatures().get(featureId);
				return (EReference) (ref instanceof EReference ? ref : null);
		
	}
	
	/**
	 * Get EMF object from a Neo4j node 
	 * if the node does not exist it creates a new node 
	 * otherwise it returns the given abject after making
	 *  sure that the new partition does not fit more to it 
	 * @param eObject {@link EObject}
	 * @param node {@link Node}
	 * @param newID {@link Integer}
	 * @param forceMove {@link Boolean}
	 * @param featureId 
	 * @return {@link EObject}
	 */
	private INeo4emfObject getObjectsFromNodeIfNotExists(EObject eObject, Node node, int newID, boolean forceMove, int featureId) {
		
		INeo4emfObject eObj = (INeo4emfObject) manager.getObjectFromProxy(node.getId());
		if (eObj == null ) {
			eObj = getObjectsFromNode(node);
			((INeo4emfObject)eObj).setPartitionId(newID);}
		else {
			int PID = forceMove ? newID :((INeo4emfObject)eObject).getPartitionId();
			((INeo4emfObject)eObj).setPartitionId(PID);
			if (forceMove){
				manager.moveToPartition(eObj,((INeo4emfObject)eObject).getPartitionId(),PID, featureId);
				manager.setUsageTrace(PID,((INeo4emfObject)eObject).getPartitionId(), featureId, eObject);
			}
			else
				manager.setUsageTrace(((INeo4emfObject)eObject).getPartitionId(),PID, featureId, eObject);
			
		}

		return eObj;
	}
	
	public EList<EClass> subClassesOf(EClass eClass) {
		EList<EClass> classesList = new BasicEList<EClass>();
		EList<EClass> allClasses = getAllClassesInPackages(eClass.getEPackage());
		for (EClass cls : allClasses){
			if (cls.getEAllSuperTypes().contains(eClass)){
				classesList.add(cls);
			}
		}
		classesList.add(eClass);
		return classesList;
	}

	private EList<EClass> getAllClassesInPackages(EPackage ePackage) {
		EList<EClass> eClassesList = new BasicEList<EClass>(); 
		for ( EClassifier eClassifier : ePackage.getEClassifiers()){
			if (eClassifier instanceof EClass)
				eClassesList.add((EClass)eClassifier );
		}
		return eClassesList;			
	}

}


