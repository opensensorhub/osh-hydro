/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
The Initial Developer is Botts Innovative Research Inc.. Portions created by the Initial
Developer are Copyright (C) 2014 the Initial Developer. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.sensor.usgswater;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.DataRecord;
import net.opengis.swe.v20.TextEncoding;
import org.sensorhub.api.data.IMultiSourceDataInterface;
import org.sensorhub.api.sensor.SensorDataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.vast.swe.SWEConstants;
import org.vast.swe.SWEHelper;
import org.vast.swe.helper.GeoPosHelper;


/**
 * <p>
 * Implementation of sensor interface for USGS Water Data using IP
 * protocol. This particular class stores configuration parameters.
 * </p>
 * 
 * @author Lee Butler <labutler10@gmail.com>
 * @since March 22, 2017
 */

public class GageHeightOutput extends AbstractSensorOutput <USGSWaterDriver> implements IMultiSourceDataInterface
{
    DataRecord dataStruct;
    TextEncoding encoding;
    Map<String, Long> latestUpdateTimes;
    Map<String, DataBlock> latestRecords = new LinkedHashMap<String, DataBlock>();

    public GageHeightOutput(USGSWaterDriver driver)
    {
        super(driver);
        latestUpdateTimes = new HashMap<String, Long>();
    }


    @Override
    public String getName()
    {
        return "gageHeight";
    }
  
    
    protected void init()
    {   
        SWEHelper swe = new SWEHelper();
        GeoPosHelper geo = new GeoPosHelper();
        
        dataStruct = swe.newDataRecord(5);
        dataStruct.setName(getName());
        dataStruct.addField("time", swe.newTimeStampIsoUTC());
        dataStruct.addField("site", swe.newText("http://sensorml.com/ont/swe/property/SiteID", "Site ID", null));
        dataStruct.getFieldList().getProperty(1).setRole(IMultiSourceDataInterface.ENTITY_ID_URI);
        dataStruct.addField("location", geo.newLocationVectorLatLon(SWEConstants.DEF_SENSOR_LOC));
        dataStruct.addField("gage_height", swe.newQuantity(SWEHelper.getPropertyUri("GageHeight"), "Gage Height", "Gage Height parameter, USGS code 00065", "[ft_i]"));
        
        // use text encoding with "," separators
        encoding = swe.newTextEncoding(",", "\n");
    }
    
    
    public void publishData(List<USGSDataRecord> dataRec)
    {
    	for (USGSDataRecord rec : dataRec)
    	{
    		DataBlock dataBlock = dataStruct.createDataBlock();
    		
    		int blockPos = 0;
    		dataBlock.setDoubleValue(blockPos++, rec.getTimeStamp()/1000);
    		dataBlock.setStringValue(blockPos++, rec.getSiteCode());
    		dataBlock.setDoubleValue(blockPos++, rec.getSiteLat());
    		dataBlock.setDoubleValue(blockPos++, rec.getSiteLon());
    		dataBlock.setFloatValue(blockPos++, rec.getDataValue());
    		
    		latestUpdateTimes.put(rec.getSiteCode(), rec.getTimeStamp());
    		latestRecordTime = System.currentTimeMillis();
    		latestRecord = dataBlock;
    		latestRecords.put(rec.getSiteCode(), latestRecord); 
    		eventHandler.publishEvent(new SensorDataEvent(latestRecordTime, rec.getSiteCode(), GageHeightOutput.this, latestRecord));
    	}
    }



    @Override
    public DataComponent getRecordDescription()
    {
        return dataStruct;
    }


    @Override
    public DataEncoding getRecommendedEncoding()
    {
        return encoding;
    }

    protected void start()
    {
    }

	public void stop()
	{	
	}
	
    @Override
    public Collection<String> getEntityIDs()
    {
        return parentSensor.getEntityIDs();
    }


    @Override
    public Map<String, DataBlock> getLatestRecords()
    {
        return Collections.unmodifiableMap(latestRecords);
    }


    @Override
    public DataBlock getLatestRecord(String entityID)
    {
        return latestRecords.get(entityID);
    }


	@Override
	public double getAverageSamplingPeriod() {
		return 0;
	}
}