#!/bin/bash

GITURL=$(git config --get remote.origin.url)

echo $GITURL

if test -d /path/to/directory; then
  echo "html Directory exists."
  cd html
  git pull
else
	git clone $GITURL html
	cd html
fi

if ( git checkout origin/gh-pages -b gh-pages) then
	echo "Checked out $GITURL gh-pages"
else
	echo "Creating out $GITURL gh-pages"
	git checkout origin/development -b gh-pages
	rm -r *
	echo "# A simple README file for the gh-pages branch" > README.md
	git add README.md
	git commit -m"Replaced gh-pages html with simple readme"
	git push -u origin gh-pages
fi
cd ..

doxygen doxy.doxyfile

cd html
git add * 
git add search/*
git commit -a -m"updating the doxygen"
git push
cd ..

git checkout development