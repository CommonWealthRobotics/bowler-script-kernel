/**
 * 
 */
package junit.bowler;

import static org.junit.Assert.*;

import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.vitamins.Vitamins;

/**
 * @author hephaestus
 *
 */
public class VitaminsTests {

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
	}

	@Test
	public void test() {
		for(String vitaminsType: Vitamins.listVitaminTypes()){
			HashMap<String, Object> meta = Vitamins.getMeta(vitaminsType);
			System.out.println("Type = "+vitaminsType);
			for(String vitaminSize:Vitamins.listVitaminSizes(vitaminsType)){
				if(!meta.isEmpty()){
					System.out.println("Meta configurations"+meta);
					try {
						//System.out.println(Vitamins.get(vitaminsType,vitaminSize));
					} catch (Exception e) {
						e.printStackTrace();
						fail();
					}
				}
				System.out.println("\tConfig = "+vitaminSize);
				HashMap<String, Object> config = Vitamins.getConfiguration(vitaminsType, vitaminSize);
				for(String param: config.keySet()){
					System.out.println("\t\t"+param+" = "+config.get(param));
				}
				
			}
		}
	}

}
