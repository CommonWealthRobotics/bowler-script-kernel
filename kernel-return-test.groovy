

[
[
new Wedge(60,25,40).toCSG().movey(75),
new Isosceles(60,25,40).toCSG()
],
DeviceManager.getSpecificDevice( "CarlTheWalkingRobot",{
			//If the device does not exist, prompt for the connection
			
			MobileBase m = MobileBaseLoader.fromGit(
				"https://github.com/madhephaestus/carl-the-hexapod.git",
				"CarlTheRobot.xml"
				)
			if(m==null)
				throw new RuntimeException("Arm failed to assemble itself")
			println "Connecting new device robot arm "+m
			return m
		}), null,[null,new Wedge(60,25,40).toCSG().movey(-75)]
		]