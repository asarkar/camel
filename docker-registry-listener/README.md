This application responds to a [Docker Registry notification](https://docs.docker.com/registry/notifications/) by updating
all Git repos in a [GitLab group](https://gitlab.com/abhijitsarkar.org) that have a file containing a list of matching Docker images.
It also keeps an audit trail in a MongoDB.

To run:

1. Start a MongoDB container (see instruction below).

2. Run [Application.kt](src/main/kotlin/org/abhijitsarkar/camel/Application.kt). This starts the app listening on port 8080.

3. Run [TestApplication.kt](src/test/kotlin/org/abhijitsarkar/camel/TestApplication.kt). This runs an end-to-end scenario,
complete with an audit trail in the end.

*OR*

3. Manually make a POST request as shown below (a sample [envelope](src/test/resources/envelope.json) is available). The application will return immediately with a list of event ids and continue in the background.
Making a GET request with an event id returns the audit trail for that event, provided the request is complete.


**POST Request**
```
curl -v -H "Accept: application/json" -H "Content-Type: application/json" -d @envelope.json "http://localhost:8080/events"
```

**GET Request**
```
curl -v -H "Accept: application/json" "http://localhost:8080/events/<eventId>"
```

MongoDB Operations
===

**Start MongoDB container**
```
docker run --name mongo -p 27017:27017 -d mongo
```

**Connect to Mongo Shell**
```
docker exec -it mongo mongo
```

**Set Database**
```
use listener
```

**Find All Events**
```
db.events.find()
```

**Delete All Events**
```
db.events.remove({})
```

**Find All Updates for an Event ID**

```
db.events.aggregate([
  {
    $unwind: "$events"
   },
   {
     $lookup:
       {
         from: "updates",
         localField: "events.id",
         foreignField: "eventId",
         as: "updates"
       }
    },
    { 
      $project: 
        { 
          updates: 1,
          event: "$events"
        }
    },
    { 
      $match:
        { 
          "event.id": "5025838b-5996-4eba-92e5-f05c6638ad9c" 
        }
    }
])
```

> 1. To find all updates for all events, remove the `$match` stage.

> 2. `updates: 1` indicates that we want to retain whatever is in the field `updates`.
