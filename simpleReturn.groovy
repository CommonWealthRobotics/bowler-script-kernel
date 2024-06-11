// code here

import eu.mihosoft.vrl.v3d.CSG
import eu.mihosoft.vrl.v3d.Cube

CSG nametagBase = new Cube(70,30,3).toCSG()

double distanceToBottom = nametagBase.getMinZ()

nametagBase=nametagBase.movez(-distanceToBottom)
			.toXMin()
			.toYMin()

double distancetoTop = nametagBase.getMaxZ()




CSG name = CSG.text("Mr. Harrington", 2)
				.movez(distancetoTop)

double xscale = (nametagBase.getTotalX()-2)/name.getTotalX()

double yScale = (nametagBase.getTotalY()-2)/name.getTotalY()

name=name
		.toXMin()
		.toYMin()
		.scalex(xscale)
		.scaley(yScale)
		.movex(1)
		.movey(1)
CSG tag = nametagBase.union(name)
			.setName("KevinsNametag")


return tag

