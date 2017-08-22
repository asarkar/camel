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
