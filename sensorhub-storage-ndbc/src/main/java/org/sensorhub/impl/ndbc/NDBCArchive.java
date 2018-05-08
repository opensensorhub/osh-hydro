package org.sensorhub.impl.ndbc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.persistence.DataKey;
import org.sensorhub.api.persistence.IDataFilter;
import org.sensorhub.api.persistence.IDataRecord;
import org.sensorhub.api.persistence.IFoiFilter;
import org.sensorhub.api.persistence.IMultiSourceStorage;
import org.sensorhub.api.persistence.IObsFilter;
import org.sensorhub.api.persistence.IObsStorage;
import org.sensorhub.api.persistence.IObsStorageModule;
import org.sensorhub.api.persistence.IRecordStoreInfo;
import org.sensorhub.api.persistence.IStorageModule;
import org.sensorhub.api.persistence.StorageException;
import org.sensorhub.impl.module.AbstractModule;
import org.sensorhub.impl.persistence.FilterUtils;
import org.sensorhub.impl.persistence.FilteredIterator;
import org.vast.sensorML.SMLHelper;
import org.vast.util.Bbox;
import org.vast.util.DateTimeFormat;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import net.opengis.gml.v32.AbstractFeature;
import net.opengis.sensorml.v20.AbstractProcess;
import net.opengis.sensorml.v20.PhysicalSystem;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;

public class NDBCArchive extends AbstractModule<NDBCConfig> implements IObsStorageModule<NDBCConfig>, IMultiSourceStorage<IObsStorage>
{
	static final String BASE_NDBC_URL = "https://sdf.ndbc.noaa.gov";
	static final String UID_PREFIX = "urn:ioos:";
	
	Map<String, RecordStore> dataStores = new LinkedHashMap<>();
    Map<String, AbstractFeature> fois = new LinkedHashMap<>();
    Bbox foiExtent = new Bbox();
    PhysicalSystem systemDesc;
    Map<String, String[]> sensorOfferings = new LinkedHashMap<>();

	@Override
	public void start() throws SensorHubException
    {
//		getCapabilities();
		loadFois();
//		for (Entry<String, AbstractFeature> entry : fois.entrySet()) {
//    		System.out.println(entry.getKey() + " | " + entry.getValue().getUniqueIdentifier() + " | " + entry.getValue().getName() + " | " + entry.getValue().getLocation() + " | " + entry.getValue().getDescription());
//    		System.out.println(entry.getValue().getDescription().split("->")[1].split(" ").length);
//    	}
//		System.out.println("");
		
		
//		initRecordStores(sensorOfferings);
		initRecordStores();
//		for (Entry<String, RecordStore> entry : dataStores.entrySet()) {
//    		System.out.println(entry.getKey());
//    		System.out.println(entry.getValue().getRecordDescription());
//    	}
		
		initSensorNetworkDescription();
    }
	
	@Override
	public void stop() throws SensorHubException {
		// TODO Auto-generated method stub
		
	}

	protected void getCapabilities() throws SensorHubException {
		try
		{
			String capUrl = BASE_NDBC_URL + "/sos/server.php?request=GetCapabilities&service=SOS";
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.parse(new URL(capUrl).openStream());
			
			NodeList nList = doc.getElementsByTagName("sos:ObservationOffering");
//			System.out.println("Length: " + nList.getLength());
			for (int i = 0; i < nList.getLength(); i++)
			{
				Node nNode = nList.item(i);
				if (nNode.getNodeType() == Node.ELEMENT_NODE)
				{
					Element eElement = (Element) nNode;
//					System.out.println("id: " + eElement.getAttribute("gml:id"));
//					System.out.println("desc: " + eElement.getElementsByTagName("gml:description").item(0).getTextContent());
//					System.out.println("name: " + eElement.getElementsByTagName("gml:name").item(0).getTextContent());
					
					NodeList pList = eElement.getElementsByTagName("sos:procedure");
					Element pElement = (Element) pList.item(0);
//					System.out.println("proc: " + pElement.getAttribute("xlink:href"));
					
					// Repeat for sos:observedProperty
					NodeList obsList = eElement.getElementsByTagName("sos:observedProperty");
					for (int k = 0; k < obsList.getLength(); k++)
					{
						Node obsNode = obsList.item(k);
						if (obsNode.getNodeType() ==  Node.ELEMENT_NODE)
						{
							Element obsElement = (Element) obsNode;
//							System.out.println("obsProp: " + obsElement.getAttribute("xlink:href"));
						}
					}
//					System.out.println("");
				}
			}
		}
		catch (Exception e)
		{
			throw new SensorHubException("Error reading capabilities information", e);
		}
	}
	
    protected void loadFois()  throws SensorHubException {
    	// request and parse station info
        try
        {
            ObsStationLoader parser = new ObsStationLoader(this);
//            parser.loadStations(fois, sensorOfferings);
            parser.loadStations(fois);
        }
        catch (Exception e)
        {
            throw new SensorHubException("Error loading station information", e);
        }
	}
    
//    protected void initRecordStores(Map<String, String[]> sensorOfferings) throws SensorHubException
    protected void initRecordStores() throws SensorHubException
    {
        RecordStore rs = new RecordStore("buoyData", config.exposeFilter.parameters);
//        RecordStore rs = new RecordStore("buoyData", config.exposeFilter.stationIds, sensorOfferings);
        dataStores.put(rs.getName(), rs);
    }
    
    protected void initSensorNetworkDescription() throws SensorHubException
    {
        SMLHelper helper = new SMLHelper();
        systemDesc = helper.newPhysicalSystem();
        systemDesc.setUniqueIdentifier(UID_PREFIX + "network");
        systemDesc.setName("NDBC Buoy Data Network");
        systemDesc.setDescription("NDBC automated sensor network collecting buoy data at " + getNumFois(null) + " stations across the US");
        
        // add outputs
        for (RecordStore rs: dataStores.values())
            systemDesc.addOutput(rs.getName(), rs.getRecordDescription());
    }
    
	@Override
	public AbstractProcess getLatestDataSourceDescription()
	{
		return systemDesc;
	}
	
    @Override
    public List<AbstractProcess> getDataSourceDescriptionHistory(double startTime, double endTime)
    {
        return Arrays.<AbstractProcess>asList(systemDesc);
    }
    
	@Override
	public AbstractProcess getDataSourceDescriptionAtTime(double time)
	{
		return systemDesc;
	}

    @Override
    public Map<String, ? extends IRecordStoreInfo> getRecordStores()
    {
        return Collections.unmodifiableMap(dataStores);
    }


    @Override
    public int getNumMatchingRecords(IDataFilter filter, long maxCount)
    {
        // compute rough estimate here
        DataFilter ndbcFilter = getNdbcFilter(filter);
        long dt = ndbcFilter.endTime.getTime() - ndbcFilter.startTime.getTime();
        long samplingPeriod = 15*60*1000; // shortest sampling period seems to be 15min
        int numSites = ndbcFilter.stationIds.isEmpty() ? fois.size() : ndbcFilter.stationIds.size();
        return (int)(numSites * dt / samplingPeriod);
//    	return 1;
    }


    @Override
    public int getNumRecords(String recordType)
    {
        long dt = config.exposeFilter.endTime.getTime() - config.exposeFilter.startTime.getTime();
        long samplingPeriod = 15*60*1000; // shortest sampling period seems to be 15min
        int numSites = fois.size();
        return (int)(numSites * dt / samplingPeriod);
//        return 1;
    }


    @Override
    public double[] getRecordsTimeRange(String recordType)
    {
        double startTime = config.exposeFilter.startTime.getTime() / 1000.;
        double endTime = config.exposeFilter.endTime.getTime() / 1000.;
        return new double[] {startTime, endTime};
    }


    @Override
    public Iterator<double[]> getRecordsTimeClusters(String recordType)
    {
        return Arrays.asList(getRecordsTimeRange(recordType)).iterator();
    }


    @Override
    public DataBlock getDataBlock(DataKey key)
    {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public Iterator<DataBlock> getDataBlockIterator(IDataFilter filter)
    {
        final Iterator<? extends IDataRecord> it = getRecordIterator(filter);
        
        return new Iterator<DataBlock>()
        {
            @Override
            public boolean hasNext()
            {
                return it.hasNext();
//                return false;
            }

            @Override
            public DataBlock next()
            {
                return it.next().getData();
            }
        };
    }
    
	@Override
	public Iterator<? extends IDataRecord> getRecordIterator(IDataFilter filter) {
		final String recType = filter.getRecordType();
		RecordStore rs = dataStores.get(recType); 
		
		// prepare loader to fetch data from USGS web service
        final ObsRecordLoader loader = new ObsRecordLoader(this, rs.getRecordDescription());
        final DataFilter ndbcFilter = getNdbcFilter(filter);
        
        // request observations by batch and iterates through them sequentially
        final long endTime = ndbcFilter.endTime.getTime();
        final long batchLength = 8*3600*1000; // 8 hours        
        class BatchIterator implements Iterator<IDataRecord>
        {                
            Iterator<BuoyDataRecord> batchIt;
            IDataRecord next;
            long nextBatchStartTime = ndbcFilter.startTime.getTime();
            
            BatchIterator()
            {
                preloadNext();
            }
            
            protected IDataRecord preloadNext()
            {
                IDataRecord current = next;
                next = null;
                
                // retrieve next batch if needed
                if ((batchIt == null || !batchIt.hasNext()) && nextBatchStartTime <= endTime)
                {
                	ndbcFilter.startTime = new Date(nextBatchStartTime);
                    
                    // adjust batch length to avoid a very small batch at the end
                    long timeGap = 1000; // gap to avoid duplicated obs
                    long adjBatchLength = batchLength;
                    long timeLeft = endTime - nextBatchStartTime;
                    if (((double)timeLeft) / batchLength < 1.5)
                        adjBatchLength = timeLeft+timeGap;
                    ndbcFilter.endTime = new Date(Math.min(nextBatchStartTime+adjBatchLength-timeGap, endTime));
                    batchIt = nextBatch(loader, ndbcFilter, recType).iterator();
                    nextBatchStartTime += adjBatchLength;
                }
                                
                if (batchIt != null && batchIt.hasNext())
                    next = batchIt.next();
                
                return current;
            }
            
            @Override
            public boolean hasNext()
            {
                return (next != null);
            }
            

            @Override
            public IDataRecord next()
            {
                if (!hasNext())
                    throw new NoSuchElementException();
                return preloadNext();
            }
        };
        
        return new BatchIterator();
	}
	
    protected DataFilter getNdbcFilter(IDataFilter filter)
    {
        DataFilter ndbcFilter = new DataFilter();  
        
        // keep only site IDs that are in both request and config
        if (filter.getProducerIDs() != null)
        	ndbcFilter.stationIds.addAll(filter.getProducerIDs());
        
        if (filter instanceof IObsFilter)
        {
            Collection<String> fois = ((IObsFilter) filter).getFoiIDs();
            if (fois != null)
            {
                for (String foiID: fois)
                	ndbcFilter.stationIds.add(foiID.substring(ObsStationLoader.FOI_UID_PREFIX.length()));
            }
        }
        if (!config.exposeFilter.stationIds.isEmpty())
        	ndbcFilter.stationIds.retainAll(config.exposeFilter.stationIds);
                    
        // use config params
        ndbcFilter.parameters.addAll(config.exposeFilter.parameters);
        ndbcFilter.siteBbox.setMinX(config.exposeFilter.siteBbox.getMinX());
        ndbcFilter.siteBbox.setMinY(config.exposeFilter.siteBbox.getMinY());
        ndbcFilter.siteBbox.setMaxX(config.exposeFilter.siteBbox.getMaxX());
        ndbcFilter.siteBbox.setMaxY(config.exposeFilter.siteBbox.getMaxY());
        
        // init time filter
        long configStartTime = config.exposeFilter.startTime.getTime();
        long configEndTime = config.exposeFilter.endTime.getTime();
        long filterStartTime = Long.MIN_VALUE;
        long filterEndTime = Long.MAX_VALUE;
        if (filter.getTimeStampRange() != null)
        {
            filterStartTime = (long)(filter.getTimeStampRange()[0] * 1000);
            filterEndTime = (long)(filter.getTimeStampRange()[1] * 1000);
        }
        
        ndbcFilter.startTime = new Date(Math.min(configEndTime, Math.max(configStartTime, filterStartTime)));
        ndbcFilter.endTime = new Date(Math.min(configEndTime, Math.max(configStartTime, filterEndTime)));
        
        return ndbcFilter;
    }
    
    protected Collection<BuoyDataRecord> nextBatch(ObsRecordLoader loader, DataFilter filter, String recType)
    {
        try
        {
            ArrayList<BuoyDataRecord> records = new ArrayList<BuoyDataRecord>();
            
            // log batch time range
            if (getLogger().isDebugEnabled())
            {
                DateTimeFormat timeFormat = new DateTimeFormat();
                getLogger().debug("Next batch is {} - {}",
                                  timeFormat.formatIso(filter.startTime.getTime()/1000., 0),
                                  timeFormat.formatIso(filter.endTime.getTime()/1000., 0));
            }
            
            // request and parse next batch
            loader.sendRequest(filter);
            while (loader.hasNext())
            {
                DataBlock data = loader.next();
                if (data == null)
                    break;
                DataKey key = new DataKey(recType, data.getDoubleValue(0));
                records.add(new BuoyDataRecord(key, data));
            }
            
            // sort by timestamps
            Collections.sort(records);
            return records;
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error while sending request for instantaneous values");
        }
    }

    @Override
    public int getNumFois(IFoiFilter filter)
    {
        if (filter == null)
            return fois.size();
        
        Iterator<AbstractFeature> it = getFois(filter);
        
        int count = 0;
        while (it.hasNext())
        {
            it.next();
            count++;
        }
        
        return count;
    }


    @Override
    public Bbox getFoisSpatialExtent()
    {
        return foiExtent.copy();
    }


    @Override
    public Iterator<String> getFoiIDs(IFoiFilter filter)
    {
        final Iterator<AbstractFeature> it = getFois(filter);
        
        return new Iterator<String>()
        {
            @Override
            public boolean hasNext()
            {
                return it.hasNext();
            }

            @Override
            public String next()
            {
                return it.next().getUniqueIdentifier();
            }
        };
    }


    @Override
    public Iterator<AbstractFeature> getFois(final IFoiFilter filter)
    {
        Iterator<AbstractFeature> it = fois.values().iterator();
        
        return new FilteredIterator<AbstractFeature>(it)
        {
            @Override
            protected boolean accept(AbstractFeature f)
            {
                return FilterUtils.isFeatureSelected(filter, f);
            }
        };
    }


    @Override
    public Collection<String> getProducerIDs()
    {
        return Collections.unmodifiableSet(fois.keySet());
    }


    @Override
    public IObsStorage getDataStore(String producerID)
    {
        return this;
    }
    
    
    @Override
    public boolean isReadSupported()
    {
        return true;
    }
    
    
    @Override
    public boolean isWriteSupported()
    {
        return false;
    }


    @Override
    public IObsStorage addDataStore(String producerID)
    {
    	System.out.println("...adding buoy DataStore...");
        throw new UnsupportedOperationException();
    }


    @Override
    public void storeDataSourceDescription(AbstractProcess process)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public void updateDataSourceDescription(AbstractProcess process)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public void removeDataSourceDescription(double time)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public void removeDataSourceDescriptionHistory(double startTime, double endTime)
    {
        throw new UnsupportedOperationException();
    }
    

    @Override
    public void addRecordStore(String name, DataComponent recordStructure, DataEncoding recommendedEncoding)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public void storeRecord(DataKey key, DataBlock data)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public void updateRecord(DataKey key, DataBlock data)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public void removeRecord(DataKey key)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public int removeRecords(IDataFilter filter)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public void storeFoi(String producerID, AbstractFeature foi)
    {
        throw new UnsupportedOperationException();
    }
    
    
    @Override
    public void backup(OutputStream os) throws IOException
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public void restore(InputStream is) throws IOException
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public void commit()
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public void rollback()
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public void sync(IStorageModule<?> storage) throws StorageException
    {
        throw new UnsupportedOperationException();
    }
}
