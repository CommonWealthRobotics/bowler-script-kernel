

[
[
new Wedge(60,25,40).toCSG().movey(75),
new Isosceles(60,25,40).toCSG()
],
DeviceManager.getSpecificDevice( "MediumKat",{
			return ScriptingEngine.gitScriptRun(	"https://github.com/OperationSmallKat/SmallKat_V2.git", 
											"loadRobot.groovy", 
											[ "https://github.com/OperationSmallKat/greycat.git",
											  "MediumKat.xml",
											  "GameController_22"]
			  )
		})
		, null,
		[null,new Wedge(60,25,40).toCSG().movey(-75)]
		]