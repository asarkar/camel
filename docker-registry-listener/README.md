**POST Request**
```
curl -v -H "Content-Type: application/json" -X POST -d @envelope.json "http://localhost:8080/events"
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
