Feature: Generating Cachekeys that change only on dependency changes and git commits
        Scenario: Cachekeys are injected properly
		Given we have run the stageAndCacheKey task
		Then the cachekey should exist in the right location

	Scenario: CacheKeys remain the same on rebuild
		Given we have generated a cachekey
		When we generate a cachekey again
		Then the cachekeys should be the same
	
	Scenario: CacheKeys change on dependency changes
		Given we have generated a cachekey
		When we change the dependencies
		And we generate a cachekey again
		Then the cachekeys should be different
	
	Scenario: Cachekeys change on git commits to local dependencies
		Given we have generated a cachekey
		When we make a git commit to a local dependency
		And we generate a cachekey again
		Then the cachekeys should be different

	Scenario: Cachekeys change on git commits to src dir of the project
		Given we have generated a cachekey
		When we make a git commit to the src directory of the project
		And we generate a cachekey again
		Then the cachekeys should be different
